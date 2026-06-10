package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * "Green TNT" -- a tower-spawning TNT. When lit (a player with flint &amp; steel) it fuses ~4s then,
 * instead of exploding, builds the big square {@code village_tower_fix} structure centered on this
 * block (the block sits one above the tower's base), block-by-block via {@link HouseBuildManager}.
 * Sensitive blocks (banners, bell, lanterns, ...) go in last; chests get village loot. No real
 * explosion -- only the carved tower footprint is touched.
 */
public class GreenTntBlock extends Block {
    public static final int FUSE_TICKS = 80; // ~4 seconds, like vanilla TNT

    private static final ResourceLocation TOWER = new ResourceLocation(VillagerGunners.MODID, "village_tower_fix");
    private static final ResourceLocation LOOT = new ResourceLocation(VillagerGunners.MODID, "chests/basic_house");

    public GreenTntBlock(Properties properties) {
        super(properties);
    }

    /** Light the fuse: the tower builds here after {@link #FUSE_TICKS}. Server-side. */
    public void prime(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.scheduleTick(pos, this, FUSE_TICKS);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                10, 2.0, 2.0, 2.0, 0.0);
        try {
            HousePlacer.place(level, pos, Rotation.NONE, TOWER, LOOT);
        } catch (Throwable t) {
            VillagerGunners.LOGGER.error("[Go Back To Your World] Green TNT tower placement failed at {}", pos, t);
        }
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
