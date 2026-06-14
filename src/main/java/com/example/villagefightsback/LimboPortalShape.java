package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lights a player-built Limbo portal: given the air cell where the flame would land, it looks for a
 * rectangular frame of {@link ModBlocks#FAKE_OBSIDIAN} (interior 2-21 wide x 3-21 tall) enclosing that cell on
 * either horizontal axis, and -- if found -- fills the interior with green Limbo portal blocks. Mirrors the
 * vanilla nether-portal rules, but for fake obsidian instead of obsidian.
 */
public final class LimboPortalShape {
    private LimboPortalShape() {}

    private static final int MIN_W = 2, MAX_W = 21, MIN_H = 3, MAX_H = 21;

    /** Try to light a portal whose interior contains {@code firePos}. Returns true if one was built. */
    public static boolean tryLight(ServerLevel level, BlockPos firePos) {
        return tryAxis(level, firePos, Direction.Axis.X) || tryAxis(level, firePos, Direction.Axis.Z);
    }

    private static boolean tryAxis(ServerLevel level, BlockPos fire, Direction.Axis axis) {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction left = right.getOpposite();

        int l = expand(level, fire, left, MAX_W);
        int r = expand(level, fire, right, MAX_W);
        int d = expand(level, fire, Direction.DOWN, MAX_H);
        int u = expand(level, fire, Direction.UP, MAX_H);
        int width = l + r + 1;
        int height = d + u + 1;
        if (width < MIN_W || width > MAX_W || height < MIN_H || height > MAX_H) return false;

        BlockPos bottomLeft = fire.relative(left, l).relative(Direction.DOWN, d);

        // Every interior cell must be replaceable (air/fire), and the whole border must be fake obsidian.
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                BlockPos p = bottomLeft.relative(right, i).relative(Direction.UP, j);
                if (!isInterior(level, p)) return false;
            }
        }
        for (int i = -1; i <= width; i++) {
            if (!isFrame(level, bottomLeft.relative(right, i).relative(Direction.DOWN, 1))) return false; // bottom
            if (!isFrame(level, bottomLeft.relative(right, i).relative(Direction.UP, height))) return false; // top
        }
        for (int j = 0; j < height; j++) {
            if (!isFrame(level, bottomLeft.relative(left, 1).relative(Direction.UP, j))) return false;  // left col
            if (!isFrame(level, bottomLeft.relative(right, width).relative(Direction.UP, j))) return false; // right col
        }

        // Valid -> fill the interior with portal blocks.
        BlockState portal = ModBlocks.LIMBO_PORTAL.get().defaultBlockState()
                .setValue(LimboPortalBlock.AXIS, axis);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                level.setBlock(bottomLeft.relative(right, i).relative(Direction.UP, j), portal, 18);
            }
        }
        return true;
    }

    /** How far the air interior extends from {@code from} in {@code dir} (exclusive of the bounding frame). */
    private static int expand(ServerLevel level, BlockPos from, Direction dir, int max) {
        int n = 0;
        for (int i = 1; i < max; i++) {
            if (isInterior(level, from.relative(dir, i))) n++;
            else break;
        }
        return n;
    }

    private static boolean isInterior(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || s.getBlock() == net.minecraft.world.level.block.Blocks.FIRE
                || s.is(ModBlocks.LIMBO_PORTAL.get());
    }

    private static boolean isFrame(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.FAKE_OBSIDIAN.get());
    }
}
