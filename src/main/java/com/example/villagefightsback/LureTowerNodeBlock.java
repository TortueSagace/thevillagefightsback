package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The Lure Tower Node: a glowing purple ore block with an expanding-TRIANGLE ripple. Like all nodes it adds
 * +10% to the village's daily harvest; additionally, every sunset (and the instant it is created) it spawns a
 * wall defender turret directly ABOVE it (never below) whenever a Village Brain Node is in range -- but only if
 * there is no wall defender already above it, so they never stack.
 */
public class LureTowerNodeBlock extends AbstractNodeBlock {
    public LureTowerNodeBlock(Properties properties) { super(properties); }

    @Override
    public void spawnPayload(ServerLevel level, BlockPos node, int stage) {
        NodeSpawns.spawnWallDefenderAbove(level, node, stage);
    }
}
