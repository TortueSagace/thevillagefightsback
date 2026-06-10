package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * "Blue TNT" -- a house-spawning TNT. When lit (by the settler AI, or a player with flint &amp; steel), it
 * fuses for {@link #FUSE_TICKS} and then "detonates" into one of the four basic houses, centered on this
 * block, with the fake-cobblestone door facing {@link #FACING}. It does NOT do an ordinary explosion --
 * it only carves the house pocket (see {@link HousePlacer}), so nothing outside the house zone is harmed.
 *
 * The detonation runs on a scheduled block tick, so it survives chunk save/load during the fuse.
 */
public class BlueTntBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final int FUSE_TICKS = 80; // ~4 seconds, like vanilla TNT

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
        // Face the placer, so a player-lit house ends up with its door toward where they stood.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** Light the fuse: a house will spawn here after {@link #FUSE_TICKS}. Server-side. */
    public void prime(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.scheduleTick(pos, this, FUSE_TICKS);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction doorFacing = state.getValue(FACING);
        // a cosmetic "poof" (no damage), then build a random house over this block
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 1.5, 1.5, 1.5, 0.0);
        HousePlacer.place(level, pos, doorFacing, HousePlacer.HOUSES[random.nextInt(HousePlacer.HOUSES.length)]);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) {
            if (!level.isClientSide && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                prime(level, pos);
                if (held.is(Items.FLINT_AND_STEEL)) {
                    held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                } else {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
