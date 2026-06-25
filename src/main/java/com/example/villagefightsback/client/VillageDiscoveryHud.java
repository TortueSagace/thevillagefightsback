package com.example.villagefightsback.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.gui.OverlayRegistry;

/**
 * Draws the two-line "village discovered" message at the bottom of the screen (above the hotbar, NOT in chat)
 * for a few seconds when a brain node first awakens near the player. The first line is green, the second red.
 * Triggered by {@link com.example.villagefightsback.network.VillageDiscoveryPacket}; the countdown is a plain
 * client-tick timer ({@link VillageDiscoveryTicker} decrements it each unpaused tick) so it ALWAYS expires,
 * independent of world/dimension game-time.
 */
public final class VillageDiscoveryHud {
    private VillageDiscoveryHud() {}

    private static final String LINE1 = "Right click on the brain node to interact with it.";
    private static final String LINE2 = "Protect the village at any cost or suffer the consequences.";
    private static final int GREEN = 0x55FF55; // ChatFormatting.GREEN rgb
    private static final int RED = 0xFF5555;    // ChatFormatting.RED rgb
    private static final int FADE_TICKS = 20;   // fade out over the last second

    private static int ticksLeft; // counts down to 0, then the message is gone

    /** Show the message for {@code ticks} (called on the client when the discovery packet arrives). */
    public static void show(int ticks) {
        ticksLeft = ticks;
    }

    /** Decrement the timer one tick (called from {@link VillageDiscoveryTicker} each unpaused client tick). */
    public static void tickDown() {
        if (ticksLeft > 0) ticksLeft--;
    }

    /** Registered from client setup (1.19.2 has no RegisterGuiOverlaysEvent). */
    public static void register() {
        OverlayRegistry.registerOverlayAbove(ForgeIngameGui.HOTBAR_ELEMENT, "village_discovery_message",
                (gui, ps, partialTick, w, h) -> draw(ps, w, h));
    }

    private static void draw(PoseStack ps, int w, int h) {
        if (ticksLeft <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int alpha = ticksLeft < FADE_TICKS ? Math.max(16, 255 * ticksLeft / FADE_TICKS) : 255;
        int top = h - 72, bot = h - 60;
        line(ps, mc, LINE1, w / 2, top, (alpha << 24) | GREEN);
        line(ps, mc, LINE2, w / 2, bot, (alpha << 24) | RED);
    }

    private static void line(PoseStack ps, Minecraft mc, String s, int cx, int y, int color) {
        GuiComponent.drawString(ps, mc.font, s, cx - mc.font.width(s) / 2, y, color);
    }
}
