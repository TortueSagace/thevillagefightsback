package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The "limbo nest": a 5x5 bowl of ancient debris that sits in the centre of every Limbo cube big enough to
 * hold it (>= 7x7), and on a top corner of the central cube. The bowl cradles up to three Limbo Eggs (added
 * once the egg block exists -- see {@link #populate}).
 */
public final class LimboNest {
    private LimboNest() {}

    /** Build a nest centred (on the floor) inside a Limbo cube. */
    public static void placeInCubeCenter(ServerLevel level, LimboCubes.Cube c) {
        int cx = c.center(c.x0());
        int cz = c.center(c.z0());
        int floorY = c.y0() + 1; // the cube's inner floor
        buildBowl(level, cx, floorY, cz);
        populate(level, cx, floorY + 1, cz, seedFor(cx, floorY, cz));
    }

    /** Build a nest on top of a cube at the given (already chosen) top-surface position. */
    public static void placeOnTop(ServerLevel level, int cx, int topY, int cz) {
        buildBowl(level, cx, topY, cz);
        populate(level, cx, topY + 1, cz, seedFor(cx, topY, cz));
    }

    /** A 5x5 ancient-debris bowl: flat floor with a raised rim (corners highest) so eggs nestle in the middle. */
    private static void buildBowl(ServerLevel level, int cx, int baseY, int cz) {
        BlockState debris = Blocks.ANCIENT_DEBRIS.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx, z = cz + dz;
                level.setBlock(m.set(x, baseY, z), debris, 2);             // floor
                int ring = Math.max(Math.abs(dx), Math.abs(dz));          // 0 centre .. 2 edge
                int rim = ring == 2 ? 2 : ring == 1 ? 1 : 0;               // raised lip -> concave bowl
                for (int h = 1; h <= rim; h++) {
                    // only the outer ring gets walls (keep the inner 3x3 open for eggs)
                    if (ring == 2 || (ring == 1 && h == 1 && (Math.abs(dx) == 1 && Math.abs(dz) == 1))) {
                        level.setBlock(m.set(x, baseY + h, z), debris, 2);
                    }
                }
            }
        }
    }

    /** Egg placement -- filled in once {@code LIMBO_EGG} exists (Limbo eggs milestone). No-op for now. */
    private static void populate(ServerLevel level, int floorTopY, int unusedY, int cz, long seed) {
        // Intentionally empty until the Limbo Egg block is registered; cubes generated afterward get eggs.
    }

    private static long seedFor(int x, int y, int z) {
        return (long) x * 132871L ^ (long) y * 7919L ^ (long) z * 40503L;
    }
}
