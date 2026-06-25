package com.example.villagefightsback;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Limbo Gravel: marks where a creature was pulled into Limbo, and tethers it ({@link LimboData}). It FALLS like
 * real gravel -- falling does NOT free the creature (the marker just slides down). Breaking it does:
 * <ul>
 *   <li>with Silk Touch -> it becomes a "soul gravel" item ({@link LimboSoulGravelItem}); the creature stays
 *       banished while you carry/store that item;</li>
 *   <li>any other way -> the creature returns to this spot (a wither storm returns whole, taking all its
 *       sibling parts with it -- see {@link LimboManager#onGravelDestroyed});</li>
 *   <li>an UNLINKED gravel (placed from creative, tethering nothing) just drops its plain block item.</li>
 * </ul>
 * Holds a {@link LimboGravelBlockEntity} carrying render data (the held entity's colour + instability deadline).
 */
public class LimboGravelBlock extends FallingBlock implements EntityBlock {
    public static final MapCodec<LimboGravelBlock> CODEC = simpleCodec(LimboGravelBlock::new);
    private static final Set<String> FALLING = ConcurrentHashMap.newKeySet();        // dim|pos currently falling
    private static final Map<String, BlockPos> PENDING_FALL = new ConcurrentHashMap<>(); // dim@x,z -> pos it fell from
    private static final Set<String> SILK_BREAK = ConcurrentHashMap.newKeySet();     // dim|pos being silk-mined
    private static final Set<String> SUPPRESS = ConcurrentHashMap.newKeySet();       // dim|pos being removed silently

    public LimboGravelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    private static String posKey(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    private static String colKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.getX() + "," + pos.getZ();
    }

    /** LimboManager calls this just before an INTERNAL removeBlock that must NOT free the creature or drop an
     *  item (sibling cleanup, "creature left Limbo", a freed-item restore). The next onRemove at pos is silent. */
    public static void suppress(ServerLevel level, BlockPos pos) {
        SUPPRESS.add(posKey(level, pos));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LimboGravelBlockEntity(pos, state);
    }

    /** Placing a soul-gravel item re-anchors its leash (the creature stays banished, now tethered here). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && LimboSoulGravelItem.isSoulGravel(stack)) {
            LimboManager.onSoulGravelPlaced(sl, pos, stack);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean willFall = FallingBlock.isFree(level.getBlockState(pos.below())) && pos.getY() >= level.getMinBuildHeight();
        if (willFall) {
            FALLING.add(posKey(level, pos));
            PENDING_FALL.put(colKey(level, pos), pos.immutable());
            super.tick(state, level, pos, random); // spawns the falling-block entity -> setBlock(air) -> onRemove
            FALLING.remove(posKey(level, pos));
        } else {
            super.tick(state, level, pos, random);
        }
    }

    /** Note a Silk-Touch break so {@link #onRemove} converts the gravel into a soul item instead of freeing. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl
                && EnchantmentHelper.getItemEnchantmentLevel(
                        sl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                                .getOrThrow(Enchantments.SILK_TOUCH),
                        player.getMainHandItem()) > 0) {
            SILK_BREAK.add(posKey(level, pos));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            String k = posKey(sl, pos);
            boolean silk = SILK_BREAK.remove(k); // always consume the flag
            if (FALLING.contains(k)) {
                // just falling -> handled by onLand; do nothing here
            } else if (SUPPRESS.remove(k)) {
                // internal silent removal -> neither free nor drop
            } else {
                LimboData.Entry entry = LimboData.get(sl.getServer()).getByGravel(sl.dimension(), pos);
                if (entry != null) {
                    if (silk) LimboManager.onGravelSilkHarvested(sl, pos);   // -> soul item, creature stays
                    else LimboManager.onGravelDestroyed(sl, pos);            // -> return the creature
                } else {
                    // an unlinked (decorative) gravel -> drop its plain block item (block has noLootTable)
                    popResource(sl, pos, new ItemStack(ModBlocks.LIMBO_GRAVEL_ITEM.get()));
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaceable, FallingBlockEntity fallingBlock) {
        if (level instanceof ServerLevel sl) {
            BlockPos from = PENDING_FALL.remove(colKey(sl, pos));
            if (from != null && !from.equals(pos)) LimboManager.onGravelLanded(sl, from, pos);
        }
        super.onLand(level, pos, state, replaceable, fallingBlock);
    }

    /** The falling gravel could NOT re-place (landed on a non-replaceable surface, etc.) -> it never re-homed, so
     *  its leash still points at the now-air origin cell. This is a PHYSICS loss, not a deliberate break: for a
     *  multi-part wither storm it just drops that one redundant part (the storm stays banished); for a lone
     *  creature it returns it (so it is never stranded). See {@link LimboManager#onGravelFellAndBroke}. */
    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        if (level instanceof ServerLevel sl) {
            BlockPos from = PENDING_FALL.remove(colKey(sl, pos));
            if (from != null) LimboManager.onGravelFellAndBroke(sl, from);
        }
        super.onBrokenAfterFall(level, pos, fallingBlock);
    }

    /** An anchor block must not be shoved off its spot by pistons (which would wrongly free the creature). */
    @Override
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) {
        return net.minecraft.world.level.material.PushReaction.BLOCK;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (level instanceof ServerLevel sl && sl.random.nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.SOUL, entity.getX(), pos.getY() + 1.0, entity.getZ(), 2, 0.2, 0.0, 0.2, 0.01);
        }
    }

    /** Ambient soul particles, like soul soil. The &gt;150 HP "unstable" purple GLOW is drawn by the block
     *  entity renderer ({@code LimboGravelRenderer}), not by particles. Client-side. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.SOUL,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0.0, 0.02 + random.nextDouble() * 0.04, 0.0);
        }
    }
}
