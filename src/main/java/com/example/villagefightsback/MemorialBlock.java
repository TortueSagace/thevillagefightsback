package com.example.villagefightsback;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Village Memorial: this mod's own headstone for a fallen defender.
 *
 * <p>It exists so the burial system works in a pack WITHOUT MCA Reborn. When MCA is installed the village buries
 * its dead in MCA's tombstones instead, so its graveyards, its mourners and its own Scythe all recognise them;
 * this block is still registered and placeable either way, it simply is not what an automatic burial reaches
 * for. Both kinds of grave are raised by the Limbo Scythe.
 *
 * <p>Faces the player who places it, and is raised automatically facing away from the body it covers, so the
 * inscription reads from the approach.
 */
public class MemorialBlock extends BaseEntityBlock {
    /** 1.21 made {@code codec()} abstract on every Block, for the block-state-to-codec registry. The block takes
     *  only Properties, so the generated simpleCodec is exact -- nothing about the stone is lost. */
    public static final MapCodec<MemorialBlock> CODEC = simpleCodec(MemorialBlock::new);

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            HorizontalDirectionalBlock.FACING;

    /** A headstone: a thin upright slab standing on the ground, not a full cube. */
    private static final VoxelShape SHAPE_NS = Block.box(3.0, 0.0, 6.0, 13.0, 12.0, 10.0);
    private static final VoxelShape SHAPE_EW = Block.box(6.0, 0.0, 3.0, 10.0, 12.0, 13.0);

    public MemorialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** MODEL, not INVISIBLE: the stone itself is an ordinary block model; only the inscription is drawn by the
     *  block entity renderer on top of it. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MemorialBlockEntity(pos, state);
    }
}
