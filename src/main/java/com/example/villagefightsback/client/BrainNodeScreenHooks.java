package com.example.villagefightsback.client;

import com.example.villagefightsback.network.OpenBrainNodeScreenPacket;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point invoked from the network packet handler (kept separate so the packet class never
 * classloads client-only types on a dedicated server).
 */
public final class BrainNodeScreenHooks {
    private BrainNodeScreenHooks() {}

    public static void open(OpenBrainNodeScreenPacket msg) {
        Minecraft.getInstance().setScreen(new BrainNodeScreen(
                msg.level, msg.maxLevel, msg.emeralds, msg.costNext,
                msg.villagers, msg.guards, msg.golems, msg.fertilized, msg.organic, msg.reputation,
                msg.reflexLog, msg.organicLog, msg.reputationLog, msg.metalLine, msg.progressOverride));
    }
}
