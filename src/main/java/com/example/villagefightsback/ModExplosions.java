package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Small shared helper for the mod's hand-built explosions. Block destruction is handled by the vanilla
 * {@code Explosion}; the engine only ignites blocks it actually DESTROYS, so when we want "set blocks on fire"
 * to be independent of "destroy blocks" we scatter the fire ourselves with {@link #scorch}.
 */
public final class ModExplosions {
    private ModExplosions() {}

    /** Place fire on every air cell (with a sturdy block face beneath it, so no floating fire) within
     *  {@code radius} blocks of {@code center}. Server-side. */
    public static void scorch(ServerLevel level, BlockPos center, int radius) {
        long r2 = (long) radius * radius;
        for (BlockPos bp : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (center.distSqr(bp) > r2) continue;
            if (!level.getBlockState(bp).isAir()) continue;
            BlockPos below = bp.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                level.setBlockAndUpdate(bp.immutable(), Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
