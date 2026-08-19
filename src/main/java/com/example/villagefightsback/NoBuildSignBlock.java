package com.example.villagefightsback;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The "Do not build here" sign: a player's claim on a patch of ground. Settlers will not build within
 * {@link com.example.villagefightsback.infection.MarkerZones#RADIUS} blocks of one, and block infection will
 * not spread into that sphere.
 *
 * <p>It is a MARKER, not a writable sign -- the text is part of the texture, there is no block entity and
 * nothing ticks. Floor and wall placement come from {@link FaceAttachedHorizontalDirectionalBlock}, the same
 * vanilla machinery levers and buttons use, so the attach rules and the break-when-unsupported behaviour are
 * vanilla's rather than something hand-rolled.
 *
 * <p>The claim itself lives in {@code MarkerZones}, registered in {@link #setPlacedBy} and dropped in
 * {@link #onRemove}. It is keyed on the placer's UUID, and stops counting the moment that player becomes an
 * Enemy of the Village -- the village does not honour the property claims of somebody it is at war with.
 */
public class NoBuildSignBlock extends FaceAttachedHorizontalDirectionalBlock {
    /** 1.21 made {@code codec()} abstract on every Block, for the block-state-to-codec registry. */
    public static final MapCodec<NoBuildSignBlock> CODEC = simpleCodec(NoBuildSignBlock::new);

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    private static final VoxelShape FLOOR_SHAPE = Block.box(3.0D, 0.0D, 6.0D, 13.0D, 16.0D, 10.0D);
    private static final VoxelShape FLOOR_SHAPE_EW = Block.box(6.0D, 0.0D, 3.0D, 10.0D, 16.0D, 13.0D);
    private static final VoxelShape WALL_NORTH = Block.box(3.0D, 2.0D, 12.0D, 13.0D, 14.0D, 16.0D);
    private static final VoxelShape WALL_SOUTH = Block.box(3.0D, 2.0D, 0.0D, 13.0D, 14.0D, 4.0D);
    private static final VoxelShape WALL_WEST = Block.box(12.0D, 2.0D, 3.0D, 16.0D, 14.0D, 13.0D);
    private static final VoxelShape WALL_EAST = Block.box(0.0D, 2.0D, 3.0D, 4.0D, 14.0D, 13.0D);

    public NoBuildSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    /** Floor or wall only -- a sign hanging from a ceiling is not a boundary marker. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        for (Direction d : ctx.getNearestLookingDirections()) {
            BlockState state;
            if (d.getAxis() == Direction.Axis.Y) {
                if (d == Direction.UP) continue;                       // clicked a ceiling -> not supported
                state = defaultBlockState().setValue(FACE, AttachFace.FLOOR)
                        .setValue(FACING, ctx.getHorizontalDirection());
            } else {
                state = defaultBlockState().setValue(FACE, AttachFace.WALL)
                        .setValue(FACING, d.getOpposite());
            }
            if (state.canSurvive(ctx.getLevel(), ctx.getClickedPos())) return state;
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (state.getValue(FACE) == AttachFace.WALL) {
            return switch (state.getValue(FACING)) {
                case SOUTH -> WALL_SOUTH;
                case WEST -> WALL_WEST;
                case EAST -> WALL_EAST;
                default -> WALL_NORTH;
            };
        }
        Direction f = state.getValue(FACING);
        return f.getAxis() == Direction.Axis.X ? FLOOR_SHAPE_EW : FLOOR_SHAPE;
    }

    /** Claim the ground. The placer is remembered, because the claim is only honoured while they are not an enemy. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && placer != null) {
            com.example.villagefightsback.infection.MarkerZones.get(sl).add(pos.immutable(), placer.getUUID());
        }
    }

    /** Release the claim when the sign goes, however it goes. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            com.example.villagefightsback.infection.MarkerZones.get(sl).remove(pos.immutable());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
