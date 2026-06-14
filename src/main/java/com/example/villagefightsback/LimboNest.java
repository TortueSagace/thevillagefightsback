package com.example.villagefightsback;

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

    /** Per-bowl-block chance to be ancient (rather than modern) debris. */
    private static final float ANCIENT_CHANCE = 0.10F;

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

    /** A 5x5 debris bowl (90% modern / 10% ancient debris): flat floor with a raised rim (corners highest) so
     *  eggs nestle in the middle. The modern/ancient mix is deterministic per position (stable on regen). */
    private static void buildBowl(ServerLevel level, int cx, int baseY, int cz) {
        BlockState ancient = Blocks.ANCIENT_DEBRIS.defaultBlockState();
        BlockState modern = ModBlocks.MODERN_DEBRIS.get().defaultBlockState();
        net.minecraft.util.RandomSource r =
                net.minecraft.util.RandomSource.create(seedFor(cx, baseY, cz) ^ 0x4D0DE15L);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx, z = cz + dz;
                level.setBlock(m.set(x, baseY, z), debris(r, ancient, modern), 2); // floor
                int ring = Math.max(Math.abs(dx), Math.abs(dz));          // 0 centre .. 2 edge
                int rim = ring == 2 ? 2 : ring == 1 ? 1 : 0;               // raised lip -> concave bowl
                for (int h = 1; h <= rim; h++) {
                    // only the outer ring gets walls (keep the inner 3x3 open for eggs)
                    if (ring == 2 || (ring == 1 && h == 1 && (Math.abs(dx) == 1 && Math.abs(dz) == 1))) {
                        level.setBlock(m.set(x, baseY + h, z), debris(r, ancient, modern), 2);
                    }
                }
            }
        }
    }

    /** 90% modern debris, 10% ancient debris. */
    private static BlockState debris(net.minecraft.util.RandomSource r, BlockState ancient, BlockState modern) {
        return r.nextFloat() < ANCIENT_CHANCE ? ancient : modern;
    }

    /** Drop up to 3 Limbo Eggs into the 3x3 hollow of the bowl, at deterministic interior cells. */
    private static void populate(ServerLevel level, int cx, int eggY, int cz, long seed) {
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed);
        int count = 1 + r.nextInt(3); // 1..3 eggs
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) cells.add(new int[]{dx, dz});
        java.util.Collections.shuffle(cells, new java.util.Random(seed));
        BlockState egg = ModBlocks.LIMBO_EGG.get().defaultBlockState();
        for (int i = 0; i < count && i < cells.size(); i++) {
            m.set(cx + cells.get(i)[0], eggY, cz + cells.get(i)[1]);
            if (level.getBlockState(m).isAir()) level.setBlock(m, egg, 2);
        }
    }

    private static long seedFor(int x, int y, int z) {
        return (long) x * 132871L ^ (long) y * 7919L ^ (long) z * 40503L;
    }
}
