package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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

    /** Detonate the moment it is placed -- schedule the build for next tick (off the placement call). */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction doorFacing = state.getValue(FACING);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 1.5, 1.5, 1.5, 0.0);
        // Never let a placement failure take down the world tick (it would disconnect the player).
        try {
            HousePlacer.place(level, pos, doorFacing, HousePlacer.HOUSES[random.nextInt(HousePlacer.HOUSES.length)]);
        } catch (Throwable t) {
            VillagerGunners.LOGGER.error("[Go Back To Your World] Blue TNT house placement failed at {}", pos, t);
        }
    }
}
