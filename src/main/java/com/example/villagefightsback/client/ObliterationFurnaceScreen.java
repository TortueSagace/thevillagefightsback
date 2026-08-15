package com.example.villagefightsback.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Obliteration Furnace's interface: how long it burns, and what it is holding out of the world.
 *
 * <p>Drawn rather than built from a container, because there is nothing here to click. The furnace is fed by
 * right-clicking it with emeralds, so the screen only ever REPORTS -- and a report that owns no slots cannot
 * desync, cannot be hoppered, and does not need a menu type registered for it.
 *
 * <p>The countdown is live: the server sends one snapshot and the client runs it down against wall time (see
 * {@link #currentTicks()}), which costs no packets and keeps ticking while the game is paused.
 *
 * <p>The list is the point of the screen and gets the room: the fuel line is one sentence at the top, and
 * everything below it is the roll of what can no longer spawn. When it is empty the screen says so in words,
 * because a blank panel reads as broken while "Nothing obliterated yet" reads as ready.
 */
public class ObliterationFurnaceScreen extends Screen {
    private static final int PANEL_W = 220;
    private static final int ROW_H = 12;
    private static final int MAX_ROWS = 12;

    private final int snapshotTicks;
    private final long openedAtNanos;
    private final List<String> names;

    private ObliterationFurnaceScreen(int burnTicks, List<String> names) {
        super(Component.literal("Obliteration Furnace"));
        this.snapshotTicks = burnTicks;
        this.openedAtNanos = System.nanoTime();
        this.names = names;
    }

    /**
     * The countdown as it stands right now: the snapshot the server sent, less the real time since it arrived.
     *
     * <p>Counted down on the CLIENT rather than pushed from the server, so the number moves every second
     * without a packet per second per open screen. The snapshot cannot drift meaningfully while the screen is
     * up, because {@link #isPauseScreen} is false: the world keeps ticking behind it, at the same rate this
     * clock does.
     *
     * <p>Measured with {@link System#nanoTime()} and not with game ticks, which is what keeps it running while
     * the game is paused. A tick-based clock stops dead the moment the pause menu appears, and a countdown
     * that freezes is exactly what this was meant to stop being.
     */
    private int currentTicks() {
        long elapsedSeconds = (System.nanoTime() - openedAtNanos) / 1_000_000_000L;
        return Math.max(0, snapshotTicks - (int) (elapsedSeconds * 20L));
    }

    /** Called from the packet handler, client side only. */
    public static void show(int burnTicks, List<String> names) {
        Minecraft.getInstance().setScreen(new ObliterationFurnaceScreen(burnTicks, names));
    }

    /** How much bigger the countdown is than body text. It is the number the player came here to read. */
    private static final float CLOCK_SCALE = 3.0F;
    /** Footnote size for the feeding instructions, which overflowed the panel at full size. */
    private static final float SMALL_SCALE = 0.75F;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int rows = Math.min(names.size(), MAX_ROWS);
        int panelH = 116 + Math.max(1, rows) * ROW_H;
        int x = (width - PANEL_W) / 2;
        int y = (height - panelH) / 2;

        g.fill(x, y, x + PANEL_W, y + panelH, 0xE0140A22);              // deep violet panel
        g.fill(x, y, x + PANEL_W, y + 1, 0xFFA855F7);                   // amethyst rule, top and bottom
        g.fill(x, y + panelH - 1, x + PANEL_W, y + panelH, 0xFFA855F7);

        g.drawString(font, Component.literal("OBLITERATION FURNACE").withStyle(ChatFormatting.LIGHT_PURPLE),
                x + 10, y + 9, 0xFFFFFF, false);

        // THE COUNTDOWN, big. Everything else on this screen is reference; this is the thing that decides
        // whether the player needs to do something in the next minute, so it is readable at a glance.
        drawClock(g, x, y + 24);

        // How to feed it. The furnace has no slot on purpose, which is exactly why the screen has to say so:
        // a player who has met every other machine in Minecraft will look for one and find nothing.
        //
        // Drawn SMALL. At full size the instruction ran the whole width of the panel and touched the frame,
        // which reads as text that has overflowed rather than text that fits. It is a footnote, not a heading,
        // so it is sized like one.
        drawSmall(g, "Right-click the furnace with emeralds to feed it.", x + 10, y + 70, 0xFFAAAAAA);
        drawSmall(g, "Emerald = 1 min", x + 10, y + 82, 0xFF55FF55);
        drawSmall(g, "Emerald Block = 10 min", x + 96, y + 82, 0xFF55FF55);

        g.fill(x + 10, y + 96, x + PANEL_W - 10, y + 97, 0x40A855F7);   // hairline before the roll

        g.drawString(font, Component.literal("Cannot spawn while this burns:")
                .withStyle(ChatFormatting.DARK_PURPLE), x + 10, y + 102, 0xFFFFFF, false);

        if (names.isEmpty()) {
            g.drawString(font, Component.literal("Nothing obliterated yet.").withStyle(ChatFormatting.DARK_GRAY),
                    x + 16, y + 116, 0xFFFFFF, false);
            return;
        }
        for (int i = 0; i < rows; i++) {
            g.drawString(font, Component.literal("- " + names.get(i)).withStyle(ChatFormatting.WHITE),
                    x + 16, y + 116 + i * ROW_H, 0xFFFFFF, false);
        }
        if (names.size() > MAX_ROWS) {
            g.drawString(font, Component.literal("... and " + (names.size() - MAX_ROWS) + " more")
                    .withStyle(ChatFormatting.DARK_GRAY), x + 16, y + 116 + rows * ROW_H, 0xFFFFFF, false);
        }
    }

    /**
     * The remaining burn time, drawn {@value #CLOCK_SCALE}x.
     *
     * <p>Scaling the pose rather than asking for a bigger font, because Minecraft ships one font size. The
     * draw coordinates are divided by the same factor so the text lands where the untransformed layout says
     * it should, which is the part that is easy to get wrong and ends with the number off the panel.
     */
    private void drawClock(GuiGraphics g, int panelX, int panelY) {
        int ticks = currentTicks();
        boolean lit = ticks > 0;
        String clock = lit ? clockText(ticks) : "COLD";
        int colour = lit ? 0xFFE9D5FF : 0xFF6B7280;

        g.pose().pushPose();
        g.pose().scale(CLOCK_SCALE, CLOCK_SCALE, 1.0F);
        int w = font.width(clock);
        float cx = (panelX + PANEL_W / 2.0F) / CLOCK_SCALE - w / 2.0F;
        float cy = panelY / CLOCK_SCALE;
        g.drawString(font, clock, (int) cx, (int) cy, colour, true);
        g.pose().popPose();

        g.drawString(font, Component.literal(lit ? "remaining" : "feed it to relight it")
                        .withStyle(ChatFormatting.DARK_GRAY),
                panelX + PANEL_W / 2 - font.width(lit ? "remaining" : "feed it to relight it") / 2,
                panelY + 30, 0xFFFFFF, false);
    }

    /**
     * Draw {@code text} at {@value #SMALL_SCALE} of normal size, positioned as if it were normal size.
     *
     * <p>Minecraft ships exactly one font, so the only way to make text smaller is to scale the pose and
     * divide the coordinates back out. Same mechanism as the big clock, in the other direction.
     */
    private void drawSmall(GuiGraphics g, String text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0F);
        g.drawString(font, text, (int) (x / SMALL_SCALE), (int) (y / SMALL_SCALE), colour, false);
        g.pose().popPose();
    }

    /** m:ss while under an hour, h:mm above it, so the big number never overflows the panel. */
    private static String clockText(int ticks) {
        int totalSeconds = ticks / 20;
        int hours = totalSeconds / 3600;
        if (hours > 0) return hours + ":" + String.format("%02d", (totalSeconds % 3600) / 60);
        return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
