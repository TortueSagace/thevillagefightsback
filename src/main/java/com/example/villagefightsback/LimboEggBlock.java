package com.example.villagefightsback;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Limbo Egg: a black-and-white "dragon egg" that nestles in Limbo nests. Unlike a real dragon egg it does
 * NOT teleport when clicked. Destroyed by ANY means it hatches into a {@link LimboPhantomEntity} -- UNLESS it
 * is mined with Silk Touch, in which case it drops as an item (its loot table). It also has a small chance to
 * hatch on its own. It falls like a dragon egg; falling alone does not hatch it.
 */
public class LimboEggBlock extends FallingBlock {
    public static final MapCodec<LimboEggBlock> CODEC = simpleCodec(LimboEggBlock::new);
    private static final int NATURAL_HATCH_CHANCE = 800; // 1/this per random tick

    private static final Set<String> FALLING = ConcurrentHashMap.newKeySet();    // dim|pos currently falling
    private static final Set<String> SILK_BREAK = ConcurrentHashMap.newKeySet(); // dim|pos being silk-mined

    public LimboEggBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean willFall = FallingBlock.isFree(level.getBlockState(pos.below())) && pos.getY() >= level.getMinBuildHeight();
        if (willFall) {
            FALLING.add(key(level, pos));            // a fall, not a destruction -> don't hatch
            super.tick(state, level, pos, random);
            FALLING.remove(key(level, pos));
        } else {
            super.tick(state, level, pos, random);
        }
    }

    /** Note a Silk-Touch break so {@link #onRemove} drops the egg instead of hatching it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl
                && EnchantmentHelper.getItemEnchantmentLevel(
                        sl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                                .getOrThrow(Enchantments.SILK_TOUCH),
                        player.getMainHandItem()) > 0) {
            SILK_BREAK.add(key(level, pos));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            String k = key(sl, pos);
            boolean silk = SILK_BREAK.remove(k);
            if (!silk && !FALLING.contains(k)) {
                hatch(sl, pos); // destroyed by any non-silk means -> hatch
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(NATURAL_HATCH_CHANCE) == 0) {
            level.removeBlock(pos, false); // -> onRemove (not falling, not silk) -> hatch
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** Spawn a Limbo Phantom where the egg was, with a hatching burst. */
    private static void hatch(ServerLevel level, BlockPos pos) {
        LimboPhantomEntity phantom = ModEntities.LIMBO_PHANTOM.get().create(level);
        if (phantom != null) {
            phantom.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            phantom.setPersistenceRequired(); // hatched in a cube -> stays put
            level.addFreshEntity(phantom);
        }
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                30, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                15, 0.4, 0.4, 0.4, 0.02);
        level.playSound(null, pos, SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.2F, 0.6F);
    }
}
