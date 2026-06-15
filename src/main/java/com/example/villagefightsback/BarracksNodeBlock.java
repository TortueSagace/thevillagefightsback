package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The Barracks Node: a glowing orange ore block with an expanding-SQUARE ripple. Like all nodes it adds +10%
 * to the village's daily harvest; additionally, every sunset (and the instant it is created) it spawns FOUR
 * guards below it -- or above, if there is no room below -- one per second so they don't all pop in at once,
 * each armed to the brain node's current stage, whenever a Village Brain Node is in range.
 */
public class BarracksNodeBlock extends AbstractNodeBlock {
    public BarracksNodeBlock(Properties properties) { super(properties); }

    @Override
    public void spawnPayload(ServerLevel level, BlockPos node, int stage) {
        NodeSpawnScheduler.scheduleGuards(level, node, stage, 4);
    }
}
