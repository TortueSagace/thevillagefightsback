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
 * <p>The list is the point of the screen and gets the room: the fuel line is one sentence at the top, and
 * everything below it is the roll of what can no longer spawn. When it is empty the screen says so in words,
 * because a blank panel reads as broken while "Nothing obliterated yet" reads as ready.
 */
public class ObliterationFurnaceScreen extends Screen {
    private static final int PANEL_W = 220;
    private static final int ROW_H = 12;
    private static final int MAX_ROWS = 12;

    private final int burnTicks;
    private final List<String> names;

    private ObliterationFurnaceScreen(int burnTicks, List<String> names) {
        super(Component.literal("Obliteration Furnace"));
        this.burnTicks = burnTicks;
        this.names = names;
    }

    /** Called from the packet handler, client side only. */
    public static void show(int burnTicks, List<String> names) {
        Minecraft.getInstance().setScreen(new ObliterationFurnaceScreen(burnTicks, names));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int rows = Math.min(names.size(), MAX_ROWS);
        int panelH = 54 + Math.max(1, rows) * ROW_H;
        int x = (width - PANEL_W) / 2;
        int y = (height - panelH) / 2;

        g.fill(x, y, x + PANEL_W, y + panelH, 0xE0140A22);              // deep violet panel
        g.fill(x, y, x + PANEL_W, y + 1, 0xFFA855F7);                   // amethyst rule, top and bottom
        g.fill(x, y + panelH - 1, x + PANEL_W, y + panelH, 0xFFA855F7);

        g.drawString(font, Component.literal("OBLITERATION FURNACE").withStyle(ChatFormatting.LIGHT_PURPLE),
                x + 10, y + 10, 0xFFFFFF, false);

        String fuel = burnTicks > 0
                ? "Burning: " + minutes(burnTicks) + " left"
                : "Cold. Feed it emeralds to relight it.";
        g.drawString(font, Component.literal(fuel)
                .withStyle(burnTicks > 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY),
                x + 10, y + 24, 0xFFFFFF, false);

        g.drawString(font, Component.literal("Cannot spawn while this burns:")
                .withStyle(ChatFormatting.DARK_PURPLE), x + 10, y + 40, 0xFFFFFF, false);

        if (names.isEmpty()) {
            g.drawString(font, Component.literal("Nothing obliterated yet.").withStyle(ChatFormatting.DARK_GRAY),
                    x + 16, y + 54, 0xFFFFFF, false);
            return;
        }
        for (int i = 0; i < rows; i++) {
            g.drawString(font, Component.literal("- " + names.get(i)).withStyle(ChatFormatting.WHITE),
                    x + 16, y + 54 + i * ROW_H, 0xFFFFFF, false);
        }
        if (names.size() > MAX_ROWS) {
            g.drawString(font, Component.literal("... and " + (names.size() - MAX_ROWS) + " more")
                    .withStyle(ChatFormatting.DARK_GRAY), x + 16, y + 54 + rows * ROW_H, 0xFFFFFF, false);
        }
    }

    private static String minutes(int ticks) {
        int m = ticks / (20 * 60);
        return m <= 0 ? "under a minute" : m + (m == 1 ? " minute" : " minutes");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
