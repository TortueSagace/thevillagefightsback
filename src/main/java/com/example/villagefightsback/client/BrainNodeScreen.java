package com.example.villagefightsback.client;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.client.BrainNodeUpgrades.Upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Village Brain Node upgrade screen: one row per brainNode level (1..maxLevel), each row showing its
 * upgrades as item icons. Rows for levels the village has not yet reached are dimmed (locked). Hovering an
 * icon shows the upgrade name + description. Purely informational; the tree itself is in {@link BrainNodeUpgrades}.
 */
public class BrainNodeScreen extends Screen {
    private static final int PANEL_W = 252;
    private static final int HEADER_H = 26;
    private static final int ROW_H = 28;
    private static final int FOOTER_H = 80; // emerald progress + defender-count + fertilized-count + a 2-line help note
    private static final int ICON = 16;
    private static final int ICON_GAP = 8;
    private static final int ICONS_X = 58; // x offset (from panel left) where a row's icons start

    private final int level;
    private final int maxLevel;
    private final int emeralds;
    private final int costNext;
    private final int villagers;
    private final int guards;
    private final int golems;
    private final int fertilized;
    private final List<String> reflexLog;

    private int leftPos;
    private int topPos;
    private int panelH;
    private boolean showLogs; // false = upgrade tree, true = reflex log

    public BrainNodeScreen(int level, int maxLevel, int emeralds, int costNext,
                           int villagers, int guards, int golems, int fertilized, List<String> reflexLog) {
        super(Component.literal("Village Brain Node"));
        this.level = level;
        this.maxLevel = maxLevel;
        this.emeralds = emeralds;
        this.costNext = costNext;
        this.villagers = villagers;
        this.guards = guards;
        this.golems = golems;
        this.fertilized = fertilized;
        this.reflexLog = reflexLog;
    }

    @Override
    protected void init() {
        this.panelH = HEADER_H + maxLevel * ROW_H + FOOTER_H;
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - panelH) / 2;
        // A "Logs" / "Upgrades" toggle in the header that flips between the upgrade tree and the reflex log.
        addRenderableWidget(Button.builder(Component.literal("Logs"), b -> {
            showLogs = !showLogs;
            b.setMessage(Component.literal(showLogs ? "Upgrades" : "Logs"));
        }).bounds(leftPos + PANEL_W - 62, topPos + 4, 56, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);

        // Panel background + border.
        g.fill(leftPos - 1, topPos - 1, leftPos + PANEL_W + 1, topPos + panelH + 1, 0xFF1A1A1F);
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + panelH, 0xFF2A2A33);
        drawBorder(g, leftPos, topPos, PANEL_W, panelH, 0xFF55556A);

        // Header.
        Component title = Component.literal("Village Brain Node").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        g.drawString(font, title, leftPos + 8, topPos + 8, 0xFFFFFFFF, false);

        Upgrade hovered = null;

        if (showLogs) {
            renderLog(g);
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        // One row per level.
        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            int rowY = topPos + HEADER_H + (lvl - 1) * ROW_H;
            boolean unlocked = lvl <= level;

            // Alternating row strip for readability.
            if ((lvl & 1) == 0) g.fill(leftPos + 2, rowY, leftPos + PANEL_W - 2, rowY + ROW_H - 2, 0x22000000);

            // Level label.
            int labelColor = unlocked ? 0xFFFFE060 : 0xFF707070;
            g.drawString(font, "Lvl " + lvl, leftPos + 8, rowY + (ROW_H - 8) / 2, labelColor, false);

            List<Upgrade> ups = BrainNodeUpgrades.forLevel(lvl);
            if (ups.isEmpty()) {
                Component todo = Component.literal("(no upgrades defined yet)").withStyle(ChatFormatting.DARK_GRAY);
                g.drawString(font, todo, leftPos + ICONS_X, rowY + (ROW_H - 8) / 2, 0xFF555555, false);
                continue;
            }

            int iconY = rowY + (ROW_H - ICON) / 2;
            for (int i = 0; i < ups.size(); i++) {
                Upgrade u = ups.get(i);
                int iconX = leftPos + ICONS_X + i * (ICON + ICON_GAP);
                if (u.texture() != null) {
                    g.blit(u.texture(), iconX, iconY, 0, 0, ICON, ICON, ICON, ICON); // 16x16 face/GUI texture
                } else {
                    g.renderItem(u.icon(), iconX, iconY);
                }
                if (!unlocked) {
                    g.fill(iconX, iconY, iconX + ICON, iconY + ICON, 0xB0101010); // grey-out locked
                }
                if (mouseX >= iconX && mouseX < iconX + ICON && mouseY >= iconY && mouseY < iconY + ICON) {
                    hovered = u;
                }
            }
        }

        // Footer: emerald progress toward the next level.
        int footerY = topPos + HEADER_H + maxLevel * ROW_H + 8;
        Component progress;
        if (costNext < 0) {
            progress = Component.literal("Max level reached  -  " + emeralds + " emeralds stored")
                    .withStyle(ChatFormatting.AQUA);
        } else {
            progress = Component.literal(emeralds + " / " + costNext + " emeralds to level " + (level + 1))
                    .withStyle(ChatFormatting.GREEN);
        }
        g.drawString(font, progress, leftPos + 8, footerY, 0xFFFFFFFF, false);

        // Defenders currently within the brain node's radius, by type.
        Component counts = Component.literal("Villagers: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(villagers)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   Guards: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(guards)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   Golems: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(golems)).withStyle(ChatFormatting.WHITE));
        g.drawString(font, counts, leftPos + 8, footerY + 12, 0xFFFFFFFF, false);

        // Fertilized blocks spread by the village (the green takeover footprint).
        Component fert = Component.literal("Fertilized blocks: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(fertilized)).withStyle(ChatFormatting.DARK_GREEN));
        g.drawString(font, fert, leftPos + 8, footerY + 24, 0xFFFFFFFF, false);

        // Gray help note at the very bottom, wrapped to stay inside the panel width.
        Component help = Component.literal(
                "Feed the brain node with emeralds to get Hero of the Village and be allowed to open village chests!")
                .withStyle(ChatFormatting.GRAY);
        int helpY = footerY + 40;
        for (net.minecraft.util.FormattedCharSequence line : font.split(help, PANEL_W - 16)) {
            g.drawString(font, line, leftPos + 8, helpY, 0xFFFFFFFF, false);
            helpY += 10;
        }

        super.render(g, mouseX, mouseY, partial);

        // Tooltip last, on top of everything.
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(hovered.name());
            lines.add(hovered.desc());
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** The "Logs" tab: the brain's last (up to 10) reflex actions, newest at the top, with the day number. */
    private void renderLog(GuiGraphics g) {
        Component heading = Component.literal("Reflex log (last 10)").withStyle(ChatFormatting.AQUA);
        g.drawString(font, heading, leftPos + 8, topPos + HEADER_H, 0xFFFFFFFF, false);
        int y = topPos + HEADER_H + 14;
        if (reflexLog == null || reflexLog.isEmpty()) {
            g.drawString(font, Component.literal("No reflexes yet.").withStyle(ChatFormatting.DARK_GRAY),
                    leftPos + 8, y, 0xFF555555, false);
            return;
        }
        // Newest first.
        for (int i = reflexLog.size() - 1; i >= 0; i--) {
            g.drawString(font, Component.literal(reflexLog.get(i)).withStyle(ChatFormatting.GRAY),
                    leftPos + 8, y, 0xFFFFFFFF, false);
            y += 11;
        }
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
