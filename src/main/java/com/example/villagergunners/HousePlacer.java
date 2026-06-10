package com.example.villagergunners;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Pastes one of the saved "basic house" structure templates into the world, centered on a point, with
 * the fake-cobblestone door rotated to face a chosen direction. Used by Blue TNT (and the settler AI).
 *
 * The houses are 7(x) x 8(y) x 7(z); their floor (local y=1) sits at the chosen center, the foundation
 * (local y=0) one below, so the house floor lands flush with the ground the TNT was placed on. The
 * 7x8x7 box is carved to air first, so the result is a clean house in a cleared pocket -- nothing
 * outside the box is touched (no explosion, no damage).
 *
 * Placement uses flags {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE} and {@code setKnownShape(true)} so the
 * paste does NOT run neighbour-shape updates -- that is what would otherwise pop beds/doors apart (the
 * "a plank breaks and a bed drops" symptom seen when pasting with neighbour-updating tools).
 */
public final class HousePlacer {
    private HousePlacer() {}

    /** The door (fake cobblestone) faces NORTH in the saved templates. */
    private static final Direction LOCAL_DOOR = Direction.NORTH;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // 2 | 16 = 18
    private static final int SX = 7, SY = 8, SZ = 7;

    public static final ResourceLocation[] HOUSES = {
            new ResourceLocation(VillagerGunners.MODID, "basic_house"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house2"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house3"),
            new ResourceLocation(VillagerGunners.MODID, "basic_house4"),
    };

    /**
     * Carve a 7x8x7 pocket at {@code center} and paste {@code houseId} into it, rotated so its door
     * faces {@code doorFacing}. Returns false if the template can't be loaded.
     */
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

        // pivot on the center column (x=3,z=3) -> rotation-invariant, so the house stays centered for
        // all four rotations; offset places local (3,*,3) on center and local y=0 at center.y-1.
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(3, 0, 3))
                .setKnownShape(true)        // suppress the neighbour-shape post-pass (keeps beds/doors intact)
                .setIgnoreEntities(false);

        int ox = center.getX() - 3, oy = center.getY() - 1, oz = center.getZ() - 3;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();

        // 1) carve the pocket to air (square footprint -> rotation-independent)
        for (int dx = 0; dx < SX; dx++)
            for (int dy = 0; dy < SY; dy++)
                for (int dz = 0; dz < SZ; dz++)
                    level.setBlock(m.set(ox + dx, oy + dy, oz + dz), air, FLAGS);

        // 2) paste the house
        BlockPos offset = new BlockPos(ox, oy, oz);
        template.placeInWorld(level, offset, offset, settings, level.getRandom(), FLAGS);

        // 3) a freshly placed bed should not read as "occupied"
        for (int dx = 0; dx < SX; dx++)
            for (int dy = 0; dy < SY; dy++)
                for (int dz = 0; dz < SZ; dz++) {
                    BlockState s = level.getBlockState(m.set(ox + dx, oy + dy, oz + dz));
                    if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.OCCUPIED)) {
                        level.setBlock(m, s.setValue(BedBlock.OCCUPIED, false), FLAGS);
                    }
                }
        return true;
    }
}
