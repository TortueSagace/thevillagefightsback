package com.example.villagefightsback;

import net.minecraft.world.level.block.LeavesBlock;

/**
 * Fertile / Rich leaves: a real {@link LeavesBlock} -- so heightmaps (MOTION_BLOCKING_NO_LEAVES), the wall planner
 * and leaf rendering all treat it as foliage -- with normal leaf decay. Decay distance is measured from blocks in
 * the {@code #minecraft:logs} tag, so the fertile/rich logs are added to that tag (see data/minecraft/tags) and
 * fertile leaves attached to their (fertile) tree persist, while detached ones decay like any leaves.
 */
public class FertileLeavesBlock extends LeavesBlock {
    public FertileLeavesBlock(Properties properties) {
        super(properties);
    }
}
