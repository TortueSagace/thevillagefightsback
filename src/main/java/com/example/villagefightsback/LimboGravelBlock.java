package com.example.villagefightsback;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Limbo Gravel: a darker gravel that marks where a creature was pulled into Limbo. It FALLS under gravity
 * like real gravel -- but falling does NOT free the creature: the marker simply slides down and the held
 * creature stays in Limbo. Only when the gravel is truly DESTROYED (broken, exploded, ...) is the creature
 * returned, to the marker's most recent resting spot ({@link LimboManager#onGravelDestroyed}). It puffs soul
 * particles, like soul soil, when walked on.
 *
 * To tell "fell away" from "destroyed", we flag the position for the brief instant the block turns into a
 * falling-block entity, and re-home the marker when it lands.
 */
public class LimboGravelBlock extends FallingBlock {
    private static final Set<String> FALLING = ConcurrentHashMap.newKeySet();        // dim|pos currently falling
    private static final Map<String, BlockPos> PENDING_FALL = new ConcurrentHashMap<>(); // dim@x,z -> pos it fell from

    public LimboGravelBlock(Properties properties) {
        super(properties);
    }

    private static String posKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    private static String colKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.getX() + "," + pos.getZ();
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean willFall = FallingBlock.isFree(level.getBlockState(pos.below())) && pos.getY() >= level.getMinBuildHeight();
        if (willFall) {
            // Mark this removal as a FALL (so onRemove doesn't free the creature) and remember the column.
            FALLING.add(posKey(level, pos));
            PENDING_FALL.put(colKey(level, pos), pos.immutable());
            super.tick(state, level, pos, random); // spawns the falling-block entity -> setBlock(air) -> onRemove
            FALLING.remove(posKey(level, pos));
        } else {
            super.tick(state, level, pos, random);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && !FALLING.contains(posKey(sl, pos))) {
            LimboManager.onGravelDestroyed(sl, pos); // a real destruction (not just falling) -> return the creature
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

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (level instanceof ServerLevel sl && sl.random.nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.SOUL,
                    entity.getX(), pos.getY() + 1.0, entity.getZ(),
                    2, 0.2, 0.0, 0.2, 0.01);
        }
    }

    /** Spontaneously puff soul particles, like soul soil/fire. Client-side ambient (called near the player). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(5) == 0) {
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.SOUL, x, y, z, 0.0, 0.02 + random.nextDouble() * 0.04, 0.0);
        }
    }
}
