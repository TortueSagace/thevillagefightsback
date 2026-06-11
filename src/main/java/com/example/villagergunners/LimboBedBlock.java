package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Limbo Bed: an indestructible (bedrock-tough), bedrock-textured "bed" in the Limbo cube. Right-clicking
 * it ("going to sleep") sends the sleeper back to wherever it was pulled in from -- which is implemented as
 * destroying its own Limbo-gravel marker (see {@link LimboManager#requestReturn}). Low bed-height shape so
 * you can stand on it.
 */
public class LimboBedBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 9.0, 16.0);

    public LimboBedBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            LimboManager.requestReturn(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
