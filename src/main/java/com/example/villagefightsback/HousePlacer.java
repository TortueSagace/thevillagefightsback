package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Starts a "rising building" build: it carves the structure's footprint at {@code center} instantly,
 * then hands the blocks to {@link HouseBuildManager}, which places them a few per tick (structure
 * bottom-up first, attachment/sensitive blocks last). The building is centered horizontally on
 * {@code center}, with its base one block below (so a center-marker TNT sits one above the base).
 * Nothing outside the footprint is touched (no explosion / no damage).
 */
public final class HousePlacer {
    private HousePlacer() {}

    /** The fake-cobblestone door faces NORTH in the house templates. */
    private static final Direction LOCAL_DOOR = Direction.NORTH;

    /** Loot table rolled (lazily, on first open) for the large chest inside each house. */
    private static final ResourceLocation HOUSE_LOOT = new ResourceLocation(TheVillageFightsBack.MODID, "chests/basic_house");

    public static final ResourceLocation[] HOUSES = {
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house"),
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house2"),
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house3"),
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house4"),
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house5"),
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house6"),
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house7"),
            new ResourceLocation(TheVillageFightsBack.MODID, "basic_house8"),
    };

    /**
     * The grand houses a stage-2+ village raises, via Cyan TNT. Larger and more elaborate than the eight
     * above, and -- unlike every other template in the mod -- NOT square, which is what
     * {@link #place(ServerLevel, BlockPos, Rotation, ResourceLocation, ResourceLocation, int)} had to be
     * taught to handle.
     */
    public static final ResourceLocation[] BIG_HOUSES = {
            new ResourceLocation(TheVillageFightsBack.MODID, "big_house_1"),   // 15 x 20 x 20
            new ResourceLocation(TheVillageFightsBack.MODID, "big_house_2"),   // 13 x 13 x 10
            new ResourceLocation(TheVillageFightsBack.MODID, "big_house_3"),   // 15 x 12 x 23
    };

    /** Longest horizontal side across {@link #BIG_HOUSES}; the yardstick for how much room one needs. */
    public static final int BIG_HOUSE_MAX_SIDE = 23;

    /** House path (Blue TNT): rotate so the door faces {@code doorFacing}; uses the house loot table. */
    public static boolean place(ServerLevel level, BlockPos center, Direction doorFacing, ResourceLocation houseId) {
        Rotation rotation = Rotation.NONE;
        for (Rotation r : Rotation.values()) {
            if (r.rotate(LOCAL_DOOR) == doorFacing) { rotation = r; break; }
        }
        return place(level, center, rotation, houseId, HOUSE_LOOT);
    }

    public static boolean place(ServerLevel level, BlockPos center, Rotation rotation, ResourceLocation structureId, ResourceLocation loot) {
        return place(level, center, rotation, structureId, loot, 0);
    }

    /**
     * Generic path: carve the footprint and queue an animated build of {@code structureId}, rotated by
     * {@code rotation}, with container loot from {@code loot} (or none if null). Returns false if it can't load.
     * The footprint size is read from the template, so this works for any square building.
     *
     * <p>{@code floorLayer} is the template's LOCAL y that should sit ONE BLOCK BELOW {@code center} (the TNT):
     * 0 (the default) places the template's bottom there, so a normal building rises from it. A NEGATIVE value
     * auto-anchors on the highest powder-snow layer instead -- a deep trap whose powder-snow surface lands right
     * below the TNT while the rest of the structure extends downward.
     */
    public static boolean place(ServerLevel level, BlockPos center, Rotation rotation, ResourceLocation structureId, ResourceLocation loot, int floorLayer) {
        Optional<StructureTemplate> opt = level.getStructureManager().get(structureId);
        if (opt.isEmpty()) {
            TheVillageFightsBack.LOGGER.warn("[The Village Fights Back] missing structure template {}", structureId);
            return false;
        }
        StructureTemplate template = opt.get();
        Vec3i size = template.getSize();
        int sx = size.getX(), sy = size.getY(), sz = size.getZ();
        int hx = sx / 2, hz = sz / 2;

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(hx, 0, hz))  // the template's own centre -> the build stays on the TNT
                .setKnownShape(true);


        // Read the template up front so we can (optionally) find its anchor layer before carving/placing.
        CompoundTag data = template.save(new CompoundTag());
        BlockState[] palette = readPalette(data);
        ListTag blocks = data.getList("blocks", Tag.TAG_COMPOUND);

        // floorLayer < 0 -> anchor on the highest powder-snow layer (a deep trap): that surface goes one below
        // the TNT and everything else (the lava pit) extends downward. Falls back to the template bottom (0).
        if (floorLayer < 0) {
            int snow = 0; boolean found = false;
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag b = blocks.getCompound(i);
                int idx = b.getInt("state");
                if (idx < 0 || idx >= palette.length || !palette[idx].is(Blocks.POWDER_SNOW)) continue;
                int ly = b.getList("pos", Tag.TAG_INT).getInt(1);
                if (!found || ly > snow) { snow = ly; found = true; }
            }
            floorLayer = found ? snow : 0;
        }

        int ox = center.getX() - hx, oy = center.getY() - 1 - floorLayer, oz = center.getZ() - hz;
        BlockPos offset = new BlockPos(ox, oy, oz);

        // The occupied box in WORLD coordinates. Everything below -- the two refusals, the protected-zone claim
        // and the carve -- must use this rather than the template's raw sx/sz.
        //
        // It was written when every template was square (7x7 houses, 15x15 towers), where the rotated and raw
        // extents coincide and the distinction is invisible. The big houses are the first that are not (15x20,
        // 13x10, 15x23), and there the raw box is wrong: a quarter turn swaps the axes, and an EVEN dimension
        // also shifts the result a block off centre (under CLOCKWISE_180 as well). Either way part of the house
        // would land outside the carved volume -- embedded in untouched terrain -- and outside the
        // StructureZones claim, which is what stops block infection eating it.
        //
        // So the box is measured, not derived: run the footprint's four corners through the SAME transform the
        // placement loop below uses. That is exact for any size, rotation and parity by construction, and for a
        // square template it reproduces the old ox..ox+sx-1 box to the block.
        int bx = Integer.MAX_VALUE, bxMax = Integer.MIN_VALUE;
        int bz = Integer.MAX_VALUE, bzMax = Integer.MIN_VALUE;
        for (int cx : new int[]{0, sx - 1}) {
            for (int cz : new int[]{0, sz - 1}) {
                BlockPos w = StructureTemplate.calculateRelativePosition(settings, new BlockPos(cx, 0, cz))
                        .offset(offset);
                bx = Math.min(bx, w.getX()); bxMax = Math.max(bxMax, w.getX());
                bz = Math.min(bz, w.getZ()); bzMax = Math.max(bzMax, w.getZ());
            }
        }
        int byMax = oy + sy - 1;

        // 0a) REFUSE outright if this footprint would eat a building that already exists.
        //
        // The carve below turns the whole footprint to air, and until this check existed it consulted nothing:
        // the only thing it spared was a brain node. So a settler that picked a spot on top of a house simply
        // deleted the house, and the death fallback -- which drops a building wherever a settler happened to
        // die -- could do it in the middle of the village. Every route into a build comes through here (Blue
        // TNT, Green TNT, the node TNTs, and the death fallback), so one refusal covers all of them, which no
        // amount of vetting inside the individual settler goals could.
        //
        // BEFORE the addBox below, and that ordering is load-bearing: after it, the build would always find
        // its own freshly-registered box and refuse itself.
        if (com.example.villagefightsback.infection.StructureZones.get(level)
                .intersectsForeign(bx, oy, bz, bxMax, byMax, bzMax)) {
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] Refused a build at {}: it would have carved through a building "
                    + "that is already there.", center);
            return false;
        }

        // 0b) REFUSE if a player has claimed this ground with a "Do not build here" sign. Same place as the
        // check above and for the same reason: every build route comes through here, so one refusal covers
        // Blue TNT, Green TNT, the node TNTs and the settler death fallback alike.
        if (com.example.villagefightsback.infection.MarkerZones.get(level)
                .blocksBox(bx, oy, bz, bxMax, byMax, bzMax)) {
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] Refused a build at {}: a player has claimed that ground.", center);
            return false;
        }

        // 0) Claim this exact volume as PROTECTED: block infection (fertile spread + emerald infested paths) is
        // refused inside it from now on, so the village never digests its own buildings. Every structure the
        // village raises comes through here -- Blue TNT houses, Green TNT towers, and the StructureTnt node
        // buildings -- so this one registration covers the lot, including any template added later.
        // The box is the same footprint the carve below walks, so it is exact by construction.
        com.example.villagefightsback.infection.StructureZones.get(level)
                .addBox(bx, oy, bz, bxMax, byMax, bzMax);
        // Any infection already inside the volume is about to be carved to air anyway; drop those cells from the
        // engine's frontier so it stops revisiting them (bounded: once per building, over its own volume).
        com.example.villagefightsback.infection.InfectionManager.forgetVolume(
                level, bx, oy, bz, bxMax, byMax, bzMax);

        // 1) carve the footprint to air instantly, over the ROTATED box computed above
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int wx = bx; wx <= bxMax; wx++)
            for (int dy = 0; dy < sy; dy++)
                for (int wz = bz; wz <= bzMax; wz++) {
                    m.set(wx, oy + dy, wz);
                    if (isProtectedNode(level.getBlockState(m))) continue; // never carve a brain/node block to air
                    level.setBlock(m, air, HouseBuildManager.FLAGS);
                }

        // 2) transform the template's blocks and split into structure vs. sensitive

        List<HouseBuildManager.Placement> structure = new ArrayList<>();
        List<HouseBuildManager.Placement> deferred = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            int idx = b.getInt("state");
            if (idx < 0 || idx >= palette.length) continue;
            ListTag posTag = b.getList("pos", Tag.TAG_INT);
            BlockPos local = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            BlockState state = palette[idx].mirror(Mirror.NONE).rotate(rotation);
            if (state.isAir()) continue; // already carved to air
            // Never place a Blue/Green TNT that got captured in a structure -- it would detonate and build
            // another structure inside this one (e.g. a stray Blue TNT in the tower template).
            if (state.getBlock() instanceof BlueTntBlock || state.getBlock() instanceof GreenTntBlock) continue;
            CompoundTag nbt = b.contains("nbt") ? b.getCompound("nbt") : null;
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, local).offset(offset);
            if (isProtectedNode(level.getBlockState(world))) continue; // don't overwrite the brain/node block we preserved
            boolean def = HouseBuildManager.isDeferred(state);
            (def ? deferred : structure).add(new HouseBuildManager.Placement(world, state, nbt, def));
        }

        // 3) order: structure bottom-up first, then sensitive blocks bottom-up -> queue the animation
        Comparator<HouseBuildManager.Placement> byHeight = Comparator.comparingInt(p -> p.pos().getY());
        structure.sort(byHeight);
        deferred.sort(byHeight);
        List<HouseBuildManager.Placement> order = new ArrayList<>(structure.size() + deferred.size());
        order.addAll(structure);
        order.addAll(deferred);

        List<CompoundTag> entities = rotation == Rotation.NONE
                ? readDecorativeEntities(data, ox, oy, oz)
                : new ArrayList<>(); // entity transform is only implemented for unrotated structures (the tower)
        HouseBuildManager.submit(level, order, loot, entities);
        return true;
    }

    /** A block a structure build must never bulldoze: the Village Brain Node and every node structure (golem
     *  factory / barracks / lure tower). A build that happens to overlap one -- e.g. a death-fallback settler TNT
     *  dropped near the centre -- leaves the node intact (the structure just builds around it) instead of carving
     *  it to air and wiping out the village. */
    private static boolean isProtectedNode(BlockState s) {
        Block b = s.getBlock();
        return b instanceof VillageBrainNodeBlock || b instanceof AbstractNodeBlock;
    }

    /** Decorative entities placed with the build (paintings, frames, armor stands); living mobs are skipped. */
    private static final java.util.Set<String> DECORATIVE = java.util.Set.of(
            "minecraft:item_frame", "minecraft:glow_item_frame",
            "minecraft:armor_stand", "minecraft:leash_knot");
    // Paintings are deliberately NOT on that list. The three village_tower_fix* templates carry 17 each, and
    // towers are always placed at Rotation.NONE -- the only case where template entities are placed at all --
    // so every Green TNT tower was spawning 17 real entities of pure decoration nobody sees from outside.
    // Dropping the id here is equivalent to stripping them from the .nbt (no other template has one) and is
    // reversible; the factory's item frames and the barracks armour stand are untouched.

    /** World-ready entity NBTs for the structure's decorative entities (offset only -> Rotation.NONE). */
    private static List<CompoundTag> readDecorativeEntities(CompoundTag data, int ox, int oy, int oz) {
        List<CompoundTag> out = new ArrayList<>();
        if (!data.contains("entities")) return out;
        ListTag entities = data.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entry = entities.getCompound(i);
            CompoundTag enbt = entry.getCompound("nbt");
            if (!DECORATIVE.contains(enbt.getString("id"))) continue; // skip villagers/guards/all mobs
            CompoundTag nbt = enbt.copy();
            nbt.remove("UUID"); // fresh UUID so it isn't a duplicate of the captured entity
            ListTag p = entry.getList("pos", Tag.TAG_DOUBLE);
            ListTag worldPos = new ListTag();
            worldPos.add(DoubleTag.valueOf(p.getDouble(0) + ox));
            worldPos.add(DoubleTag.valueOf(p.getDouble(1) + oy));
            worldPos.add(DoubleTag.valueOf(p.getDouble(2) + oz));
            nbt.put("Pos", worldPos);
            if (nbt.contains("TileX")) { // hanging entity (painting / item frame) -> move its anchor block too
                ListTag bp = entry.getList("blockPos", Tag.TAG_INT);
                nbt.putInt("TileX", bp.getInt(0) + ox);
                nbt.putInt("TileY", bp.getInt(1) + oy);
                nbt.putInt("TileZ", bp.getInt(2) + oz);
            }
            out.add(nbt);
        }
        return out;
    }

    /** Read the (single) block-state palette from a saved structure tag. */
    private static BlockState[] readPalette(CompoundTag data) {
        HolderGetter<Block> lookup = BuiltInRegistries.BLOCK.asLookup();
        ListTag paletteTag = data.contains("palette")
                ? data.getList("palette", Tag.TAG_COMPOUND)
                : data.getList("palettes", Tag.TAG_LIST).getList(0);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = NbtUtils.readBlockState(lookup, paletteTag.getCompound(i));
        }
        return palette;
    }
}
