package com.example.villagergunners.wall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * One planned block placement in a {@link WallPlan}: a position, the kind of block, and (for stairs and
 * ladders) the orientation. The plan stores these lightweight values instead of full {@link BlockState}s so
 * the NBT stays compact and version-stable, and so completion can be checked cheaply against the world.
 *
 * @param pos     world position of the block
 * @param type    which block to place
 * @param facing  horizontal facing for STAIR (outward) and LADDER (toward the interior); ignored for COBBLE
 * @param topHalf for STAIR only: true = inverted top-half ("Gamma"), false = normal bottom-half ("L")
 * @param course  0..4 height course within the wall (used for layer-by-layer build ordering)
 */
public record WallBlock(BlockPos pos, BlockType type, Direction facing, boolean topHalf, int course) {

    // FAKE_COBBLE appended at the END so existing saved plans' ordinals stay stable.
    public enum BlockType { COBBLE, STAIR, LADDER, TORCH, FAKE_COBBLE }

    /** The concrete block state this placement should produce. */
    public BlockState toState() {
        return switch (type) {
            case COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
            case FAKE_COBBLE -> com.example.villagergunners.ModBlocks.FAKE_COBBLESTONE.get().defaultBlockState();
            case STAIR -> Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, facing)
                    .setValue(StairBlock.HALF, topHalf ? Half.TOP : Half.BOTTOM)
                    .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
            case LADDER -> Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, facing);
            case TORCH -> Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, facing);
        };
    }

    /**
     * True if the world block already satisfies this placement (so it counts as built). Stair SHAPE is
     * intentionally ignored because vanilla auto-reshapes stairs against their neighbours.
     */
    public boolean matches(BlockState world) {
        return switch (type) {
            case COBBLE -> world.is(Blocks.COBBLESTONE);
            case FAKE_COBBLE -> world.is(com.example.villagergunners.ModBlocks.FAKE_COBBLESTONE.get());
            case STAIR -> world.is(Blocks.COBBLESTONE_STAIRS)
                    && world.getValue(StairBlock.FACING) == facing
                    && world.getValue(StairBlock.HALF) == (topHalf ? Half.TOP : Half.BOTTOM);
            case LADDER -> world.is(Blocks.LADDER)
                    && world.getValue(LadderBlock.FACING) == facing;
            case TORCH -> world.is(Blocks.WALL_TORCH)
                    && world.getValue(WallTorchBlock.FACING) == facing;
        };
    }
}
