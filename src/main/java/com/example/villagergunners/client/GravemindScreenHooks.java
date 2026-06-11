package com.example.villagergunners.client;

import com.example.villagergunners.network.OpenGravemindScreenPacket;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point invoked from the network packet handler (kept separate so the packet class never
 * classloads client-only types on a dedicated server).
 */
public final class GravemindScreenHooks {
    private GravemindScreenHooks() {}

    public static void open(OpenGravemindScreenPacket msg) {
        Minecraft.getInstance().setScreen(new GravemindScreen(
                msg.level, msg.maxLevel, msg.emeralds, msg.costNext, msg.villagers, msg.guards, msg.golems));
    }
}
