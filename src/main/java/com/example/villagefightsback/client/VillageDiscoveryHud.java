package com.example.villagefightsback.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * Draws this mod's bottom-of-screen messages (above the hotbar, NOT in chat): the two-line "village discovered"
 * message, and the one-line starter-bell hint shown to a player who spawns away from any village.
 *
 * <p>Originally just the discovery message; the hint reuses the same drawing and fade so the two look identical.
 * They keep INDEPENDENT timers and sit on separate lines, so one can never truncate or overlap the other.
 *
 * <p>The discovery part: the two-line message shown at the bottom of the screen (above the hotbar, NOT in chat)
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

    /** Shown once to a player who spawns away from any village, alongside the starter Bell they were given. */
    private static final String HINT = "If you need company, you might want to place this bell on the floor...";
    private static final int YELLOW = 0xFFFF55;  // ChatFormatting.YELLOW rgb

    private static int ticksLeft;     // discovery message: counts down to 0, then it is gone
    private static int hintTicksLeft; // starter-bell hint: its own timer, so the two never interfere

    /** Show the message for {@code ticks} (called on the client when the discovery packet arrives). */
    public static void show(int ticks) {
        ticksLeft = ticks;
    }

    /** Show the starter-bell hint for {@code ticks}, or hide it at once with 0 (the bell has been placed). */
    public static void showHint(int ticks) {
        hintTicksLeft = ticks;
    }

    /** Decrement both timers one tick (called from {@link VillageDiscoveryTicker} each unpaused client tick). */
    public static void tickDown() {
        if (ticksLeft > 0) ticksLeft--;
        if (hintTicksLeft > 0) hintTicksLeft--;
    }

    /** Registered from client setup (1.19.2 has no RegisterGuiOverlaysEvent). */
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "village_discovery_message",
                (gui, ps, partialTick, w, h) -> draw(ps, w, h));
    }

    // 1.19.2 hands the overlay a PoseStack rather than a GuiGraphics, so that is what travels through draw/line.
    // Note the discovery timer is NOT checked at the top any more: the bell hint has its own timer and must be
    // able to show on its own, so the early return moved down to just before the discovery lines.
    private static void draw(PoseStack ps, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // The hint sits one line ABOVE the discovery block, so if both are ever up at once they stack cleanly.
        if (hintTicksLeft > 0) {
            int a = hintTicksLeft < FADE_TICKS ? Math.max(16, 255 * hintTicksLeft / FADE_TICKS) : 255;
            line(ps, mc, HINT, w / 2, h - 84, (a << 24) | YELLOW);
        }

        if (ticksLeft <= 0) return;
        int alpha = ticksLeft < FADE_TICKS ? Math.max(16, 255 * ticksLeft / FADE_TICKS) : 255;
        int top = h - 72, bot = h - 60;
        line(ps, mc, LINE1, w / 2, top, (alpha << 24) | GREEN);
        line(ps, mc, LINE2, w / 2, bot, (alpha << 24) | RED);
    }

    private static void line(PoseStack ps, Minecraft mc, String s, int cx, int y, int color) {
        GuiComponent.drawString(ps, mc.font, s, cx - mc.font.width(s) / 2, y, color);
    }
}
