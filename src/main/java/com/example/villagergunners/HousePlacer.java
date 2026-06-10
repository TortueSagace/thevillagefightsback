package com.example.villagergunners;

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
    private static final ResourceLocation HOUSE_LOOT = new ResourceLocation(VillagerGunners.MODID, "chests/basic_house");

    public static final ResourceLocation[] HOUSES = {
            new ResourceLocation(VillagerGunners.MODID, "basic_house"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house2"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house3"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house4"),
    };

    /** House path (Blue TNT): rotate so the door faces {@code doorFacing}; uses the house loot table. */
    public static boolean place(ServerLevel level, BlockPos center, Direction doorFacing, ResourceLocation houseId) {
        Rotation rotation = Rotation.NONE;
        for (Rotation r : Rotation.values()) {
            if (r.rotate(LOCAL_DOOR) == doorFacing) { rotation = r; break; }
        }
        return place(level, center, rotation, houseId, HOUSE_LOOT);
    }

    /**
     * Generic path: carve the footprint and queue an animated build of {@code structureId}, rotated by
     * {@code rotation}, with container loot from {@code loot} (or none if null). Returns false if it
     * can't load. The footprint size is read from the template, so this works for any square building.
     */
    public static boolean place(ServerLevel level, BlockPos center, Rotation rotation, ResourceLocation structureId, ResourceLocation loot) {
        Optional<StructureTemplate> opt = level.getStructureManager().get(structureId);
        if (opt.isEmpty()) {
            VillagerGunners.LOGGER.warn("[Go Back To Your World] missing structure template {}", structureId);
            return false;
        }
        StructureTemplate template = opt.get();
        Vec3i size = template.getSize();
        int sx = size.getX(), sy = size.getY(), sz = size.getZ();
        int hx = sx / 2, hz = sz / 2;

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(hx, 0, hz))  // square footprint -> rotation-invariant center
                .setKnownShape(true);

        int ox = center.getX() - hx, oy = center.getY() - 1, oz = center.getZ() - hz;
        BlockPos offset = new BlockPos(ox, oy, oz);

        // 1) carve the footprint to air instantly (square footprint -> rotation-independent box)
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = 0; dx < sx; dx++)
            for (int dy = 0; dy < sy; dy++)
                for (int dz = 0; dz < sz; dz++)
                    level.setBlock(m.set(ox + dx, oy + dy, oz + dz), air, HouseBuildManager.FLAGS);

        // 2) read the template's blocks, transform them, and split into structure vs. sensitive
        CompoundTag data = template.save(new CompoundTag());
        BlockState[] palette = readPalette(data);
        ListTag blocks = data.getList("blocks", Tag.TAG_COMPOUND);

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
            CompoundTag nbt = b.contains("nbt") ? b.getCompound("nbt") : null;
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, local).offset(offset);
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

    /** Decorative entities placed with the build (paintings, frames, armor stands); living mobs are skipped. */
    private static final java.util.Set<String> DECORATIVE = java.util.Set.of(
            "minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame",
            "minecraft:armor_stand", "minecraft:leash_knot");

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
