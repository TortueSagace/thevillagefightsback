package com.example.villagefightsback.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

/**
 * A block that decides for itself how mobs path through it.
 *
 * <p>Forge asks the block directly. Vanilla works it out in WalkNodeEvaluator without consulting the block at
 * all, so a mixin there checks for this interface first -- with a null mob, exactly as the Forge hook is called
 * in 1.20.1, which is why the ally/enemy split is done through per-mob malus instead (see {@code ModPathTypes}).
 */
public interface PathTypeProvider {
    /** The path type for this block, or null to leave it to vanilla. */
    BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob);
}
