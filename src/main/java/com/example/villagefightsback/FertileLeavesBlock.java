package com.example.villagefightsback;

import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fertile / Rich leaves: a real {@link LeavesBlock} -- so heightmaps (MOTION_BLOCKING_NO_LEAVES), the wall
 * planner, and leaf rendering all treat it as foliage -- but one that NEVER decays. Leaf removal only happens on a
 * random tick (when DISTANCE == 7 and not persistent); by reporting itself as non-random-ticking the counter-
 * infection's leaves simply never decay, no matter how far they are from a log.
 */
public class FertileLeavesBlock extends LeavesBlock {
    public FertileLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }
}
