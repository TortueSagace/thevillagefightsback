package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Limbo Gravel: a darker gravel that marks where a creature stood before it was pulled into Limbo. It falls
 * under gravity like real gravel -- and the moment it is removed by ANY means (broken, exploded, or the
 * ground beneath it pulled out so it falls away), the creature it belongs to is yanked back to this exact
 * spot ({@link LimboManager#onGravelRemoved}). It puffs soul particles, like soul soil, when walked on.
 */
public class LimboGravelBlock extends FallingBlock {
    public LimboGravelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Only when the block at this position actually changes away from limbo gravel (not a same-block
        // state update) does a creature get pulled back.
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            LimboManager.onGravelRemoved(sl, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
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
}
