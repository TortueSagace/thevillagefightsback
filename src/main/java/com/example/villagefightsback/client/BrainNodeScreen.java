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
    /** Extra height the Survival metal row needs, so it never overflows the panel it sits in. */
    private static final int METAL_ROW_H = 12;
    private static final int ROW_H = 28;
    // emerald progress + defender counts + a 2-line help note. Twelve shorter than it was: the
    // fertilized-block row was removed but the footnote below it kept its old offset, so the panel
    // carried an empty row and then overran its own bottom edge by a few pixels.
    private static final int FOOTER_H = 56;
    private static final int ICON = 16;
    private static final int ICON_GAP = 8;
    private static final int ICONS_X = 58; // x offset (from panel left) where a row's icons start
    private static final int ENTITY_ICON_SCALE = 14; // display scale for a live-entity icon (e.g. wall defender)

    private final int level;
    private final int maxLevel;
    private final int emeralds;
    /** SURVIVAL only: the metal tribute line drawn under the emerald row. Empty in Creative. */
    private final String metalLine;
    /** Shown in place of the emerald line when only metal is missing. Empty otherwise. */
    private final String progressOverride;
    private final int costNext;
    private final int villagers;
    private final int guards;
    private final int golems;
    private final int fertilized;
    private final int organic;
    private final int reputation;
    private final List<String> reflexLog;
    private final List<String> organicLog;
    private final List<String> reputationLog;

    private int leftPos;
    private int topPos;
    private int panelH;
    private int view; // 0 = upgrade tree, 1 = reflex log, 2 = organic log, 3 = reputation log

    public BrainNodeScreen(int level, int maxLevel, int emeralds, int costNext,
                           int villagers, int guards, int golems, int fertilized, int organic,
                           int reputation, List<String> reflexLog, List<String> organicLog,
                           List<String> reputationLog, String metalLine, String progressOverride) {
        super(Component.literal("Village Brain Node"));
        this.metalLine = metalLine == null ? "" : metalLine;
        this.progressOverride = progressOverride == null ? "" : progressOverride;
        this.level = level;
        this.maxLevel = maxLevel;
        this.emeralds = emeralds;
        this.costNext = costNext;
        this.villagers = villagers;
        this.guards = guards;
        this.golems = golems;
        this.fertilized = fertilized;
        this.organic = organic;
        this.reputation = reputation;
        this.reflexLog = reflexLog;
        this.organicLog = organicLog;
        this.reputationLog = reputationLog;
    }

    /**
     * The view the toggle button cycles to next.
     *
     * <p>Four views now, and the organic tab is still skipped when the organic system is off -- but only that
     * one. Reputation is never optional, so the skip has to name the tab it means rather than "the last one",
     * which is what the old {@code n == 2} test amounted to.
     */
    private int nextView(int v) {
        int n = (v + 1) % 4;
        if (n == 2 && !com.example.villagefightsback.Config.organicMassEnabled()) n = 3;
        return n;
    }

    private static String viewLabel(int v) {
        return v == 0 ? "Upgrades" : v == 1 ? "Reflex log" : v == 2 ? "Organic log" : "Reputation";
    }

    @Override
    protected void init() {
        // The metal row only exists in Survival, so the panel only grows there -- a Creative world keeps the
        // exact height it always had rather than carrying a blank strip.
        this.panelH = HEADER_H + maxLevel * ROW_H + FOOTER_H + (metalLine.isEmpty() ? 0 : METAL_ROW_H);
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - panelH) / 2;
        // A header toggle that cycles Upgrades -> Reflex log -> Organic log (-> Upgrades). The button shows the
        // view it will switch TO. The organic tab is skipped when the organic system is off.
        addRenderableWidget(Button.builder(Component.literal(viewLabel(nextView(view))), b -> {
            view = nextView(view);
            b.setMessage(Component.literal(viewLabel(nextView(view))));
        }).bounds(leftPos + PANEL_W - 78, topPos + 4, 72, 16).build());
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

        if (view == 1) {
            renderLog(g, "Reflex log (last 10)", reflexLog, "No reflexes yet.");
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        if (view == 2) {
            renderLog(g, "Organic log (last 10)", organicLog, "No organic activity yet.");
            super.render(g, mouseX, mouseY, partial);
            return;
        }
        if (view == 3) {
            renderLog(g, "Reputation log (last 10)", reputationLog, "The village has no opinion yet.");
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
                if (u.entity() != null && GuiEntityIcons.render(g, iconX, iconY, u.entity(), ENTITY_ICON_SCALE)) {
                    // drawn as a live, animated entity (e.g. the spinning wall defender)
                } else if (u.texture() != null) {
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
        } else if (!progressOverride.isEmpty()) {
            // The emeralds are in; metal is what is left. Saying "12 / 8 emeralds" here would point at the
            // one thing that is NOT holding the stage back.
            progress = Component.literal(progressOverride);
        } else {
            progress = Component.literal(emeralds + " / " + costNext + " emeralds to level " + (level + 1))
                    .withStyle(ChatFormatting.GREEN);
        }
        g.drawString(font, progress, leftPos + 8, footerY, 0xFFFFFFFF, false);

        // SURVIVAL: the metal tribute, immediately under the emerald line it belongs with. Everything after it
        // is pushed down by exactly the height the panel grew by, so nothing overlaps.
        int metalShift = 0;
        if (!metalLine.isEmpty()) {
            g.drawString(font, Component.literal(metalLine), leftPos + 8, footerY + METAL_ROW_H,
                    0xFFFFFFFF, false);
            metalShift = METAL_ROW_H;
        }
        footerY += metalShift;

        // The standing row: organic mass on the LEFT when the organic system is on, and this village's opinion
        // of you on the RIGHT, always.
        //
        // `extra` is unconditional now, where it used to follow the organic flag. Reputation always occupies
        // this row, so the lines below always have to be pushed down by it -- leaving it conditional would
        // have dropped the defender counts straight on top of the reputation readout in every world with the
        // organic system switched off.
        boolean organicShown = com.example.villagefightsback.Config.organicMassEnabled();
        if (organicShown) {
            g.drawString(font, Component.literal(organic + " organic mass").withStyle(ChatFormatting.DARK_GREEN),
                    leftPos + 8, footerY + 12, 0xFFFFFFFF, false);
        }
        String rep = "Reputation: " + reputation;
        g.drawString(font, Component.literal(rep).withStyle(ChatFormatting.GOLD),
                leftPos + PANEL_W - 8 - font.width(rep), footerY + 12, 0xFFFFFFFF, false);
        int extra = 12;

        // Defenders currently within the brain node's radius, by type.
        Component counts = Component.literal("Villagers: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(villagers)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   Guards: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(guards)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   Golems: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(golems)).withStyle(ChatFormatting.WHITE));
        g.drawString(font, counts, leftPos + 8, footerY + 12 + extra, 0xFFFFFFFF, false);

        // The fertilized-block count used to sit here. Removed: it is a world-wide tally that says nothing
        // about THIS village and nothing a player can act on. The row it occupied is gone with it, so the
        // panel is a line shorter rather than carrying a gap.

        // Small light-gray footnote at the very bottom (rendered at a reduced scale), wrapped to the panel width.
        float helpScale = 0.72F;
        Component help = Component.literal(
                "Feed the brain node with emeralds to get Hero of the Village and be allowed to open village chests!")
                .withStyle(ChatFormatting.GRAY);
        int splitW = (int) ((PANEL_W - 16) / helpScale); // wider wrap to account for the smaller font
        int helpY = footerY + 40;   // was + extra, which left the removed row's gap behind
        g.pose().pushPose();
        g.pose().scale(helpScale, helpScale, 1.0F);
        for (net.minecraft.util.FormattedCharSequence line : font.split(help, splitW)) {
            // coordinates are in the scaled space, so divide the screen position by the scale
            g.drawString(font, line, (int) ((leftPos + 8) / helpScale), (int) (helpY / helpScale), 0xFFBBBBBB, false);
            helpY += 8;
        }
        g.pose().popPose();

        super.render(g, mouseX, mouseY, partial);

        // Tooltip last, on top of everything.
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(hovered.name());
            lines.add(hovered.desc());
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** A log tab: the brain's last (up to 10) actions of one kind, newest at the top, with the day number. */
    private void renderLog(GuiGraphics g, String heading, List<String> lines, String empty) {
        g.drawString(font, Component.literal(heading).withStyle(ChatFormatting.AQUA),
                leftPos + 8, topPos + HEADER_H, 0xFFFFFFFF, false);
        int y = topPos + HEADER_H + 14;
        if (lines == null || lines.isEmpty()) {
            g.drawString(font, Component.literal(empty).withStyle(ChatFormatting.DARK_GRAY),
                    leftPos + 8, y, 0xFF555555, false);
            return;
        }
        int wrapW = PANEL_W - 16; // wrap long lines (e.g. a marker on a long-named target) onto extra lines
        for (int i = lines.size() - 1; i >= 0; i--) { // newest first
            net.minecraft.network.chat.Component line = Component.literal(lines.get(i)).withStyle(ChatFormatting.GRAY);
            for (net.minecraft.util.FormattedCharSequence seq : font.split(line, wrapW)) {
                g.drawString(font, seq, leftPos + 8, y, 0xFFFFFFFF, false);
                y += 10;
            }
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
