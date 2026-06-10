package com.example.villagergunners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
 * Starts a "rising house" build: it carves a 7x8x7 pocket at {@code center} instantly, then hands the
 * house's blocks to {@link HouseBuildManager}, which places them a few per tick (structure bottom-up
 * first, attachment/sensitive blocks last). The fake-cobblestone door is rotated to face {@code doorFacing}.
 * Nothing outside the pocket is touched (no explosion / no damage).
 */
public final class HousePlacer {
    private HousePlacer() {}

    /** The door (fake cobblestone) faces NORTH in the saved templates. */
    private static final Direction LOCAL_DOOR = Direction.NORTH;
    private static final int SX = 7, SY = 8, SZ = 7;

    /** Loot table rolled (lazily, on first open) for the large chest inside each house. */
    private static final ResourceLocation LOOT = new ResourceLocation(VillagerGunners.MODID, "chests/basic_house");

    public static final ResourceLocation[] HOUSES = {
            new ResourceLocation(VillagerGunners.MODID, "basic_house"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house2"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house3"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house4"),
    };

    /** Carve the pocket and queue an animated build of {@code houseId}. Returns false if it can't load. */
    public static boolean place(ServerLevel level, BlockPos center, Direction doorFacing, ResourceLocation houseId) {
        Optional<StructureTemplate> opt = level.getStructureManager().get(houseId);
        if (opt.isEmpty()) {
            VillagerGunners.LOGGER.warn("[Go Back To Your World] missing house template {}", houseId);
            return false;
        }
        StructureTemplate template = opt.get();

        Rotation rotation = Rotation.NONE;
        for (Rotation r : Rotation.values()) {
            if (r.rotate(LOCAL_DOOR) == doorFacing) { rotation = r; break; }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(3, 0, 3))
                .setKnownShape(true);

        int ox = center.getX() - 3, oy = center.getY() - 1, oz = center.getZ() - 3;
        BlockPos offset = new BlockPos(ox, oy, oz);

        // 1) carve the pocket to air instantly (square footprint -> rotation-independent)
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = 0; dx < SX; dx++)
            for (int dy = 0; dy < SY; dy++)
                for (int dz = 0; dz < SZ; dz++)
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
            if (state.isAir()) continue; // we already carved to air
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

        HouseBuildManager.submit(level, order, LOOT);
        return true;
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
