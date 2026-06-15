package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The Golem Factory Node (formerly the Emerald Node): a glowing green ore block with an expanding-CIRCLE
 * ripple. Like all nodes it adds +10% to the village's daily harvest; additionally, every sunset (and the
 * instant it is created) it spawns an iron golem below it -- or above, if there is no room below -- whenever a
 * Village Brain Node is in range. The golem is a Gigachad Golem if that brain node is stage 3 or higher.
 */
public class GolemFactoryNodeBlock extends AbstractNodeBlock {
    public GolemFactoryNodeBlock(Properties properties) { super(properties); }

    @Override
    public void spawnPayload(ServerLevel level, BlockPos node, int stage) {
        NodeSpawns.spawnGolem(level, node, stage);
    }
}
