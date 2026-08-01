package com.example.villagefightsback.client;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point invoked from the network packet handler (kept separate so the packet class never
 * classloads client-only types on a dedicated server).
 */
public final class ModeSelectHooks {
    private ModeSelectHooks() {}

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ModeSelectScreen) return; // already up (a relog while it was open)
        mc.setScreen(new ModeSelectScreen());
    }
}
