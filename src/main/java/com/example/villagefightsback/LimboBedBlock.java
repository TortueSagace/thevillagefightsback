package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Limbo Bed: a real two-part (head + foot) black bed -- but rendered from STATIC block models (not the
 * vanilla bed renderer) so its blanket/mattress is textured with bedrock while the frame, legs and pillow
 * stay black. Indestructible (bedrock-tough). Right-clicking it ("sleeping") sends the sleeper back to where
 * it was pulled in from, by destroying its own Limbo-gravel marker (see {@link LimboManager#requestReturn}).
 */
public class LimboBedBlock extends BedBlock {
    public LimboBedBlock(Properties properties) {
        super(DyeColor.BLACK, properties);
    }

    /** Render from our block models (the bedrock-blanket look), NOT the vanilla animated bed renderer. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** No bed block-entity needed (we don't use the bed renderer / colour). */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
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
