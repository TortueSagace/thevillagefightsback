package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * "Blue TNT" -- a house-spawning TNT. It detonates the instant it is placed (by a player or the settler
 * AI): instead of a real explosion it builds one of the four basic houses, centered on this block, with
 * the fake-cobblestone door facing {@link #FACING}. No explosion damage -- only the house pocket is carved.
 * The detonation runs on a 1-tick scheduled block tick (so it happens off the placement call, not synchronously).
 */
public class BlueTntBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Ticks the founding flow budgets for a settler's house to finish building after it's dropped. */
    public static final int FUSE_TICKS = 80;

    public BlueTntBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Face the placer, so a player-placed house ends up with its door toward where they stood.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /**
     * Detonate the moment it is placed. The build is captured here (door facing + position) and run on the
     * next SERVER tick via {@link HouseBuildManager}, so it fires reliably -- even if a neighbouring TNT's
     * footprint carves this block away first (a scheduled block tick would have been silently dropped).
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!(level instanceof ServerLevel sl)) return;
        Direction doorFacing = state.getValue(FACING);
        BlockPos at = pos.immutable();
        HouseBuildManager.detonateNextTick(sl, at, () -> {
            sl.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.0F);
            sl.sendParticles(ParticleTypes.EXPLOSION, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    8, 1.5, 1.5, 1.5, 0.0);
            HousePlacer.place(sl, at, doorFacing, HousePlacer.HOUSES[sl.random.nextInt(HousePlacer.HOUSES.length)]);
        });
    }
}
