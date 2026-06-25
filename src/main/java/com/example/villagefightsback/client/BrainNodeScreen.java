package com.example.villagefightsback.client;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.client.BrainNodeUpgrades.Upgrade;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
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
    private static final int ENTITY_ICON_SCALE = 14; // display scale for a live-entity icon (e.g. wall defender)

    private final int level;
    private final int maxLevel;
    private final int emeralds;
    private final int costNext;
    private final int villagers;
    private final int guards;
    private final int golems;
    private final int fertilized;
    private final int organic;
    private final List<String> reflexLog;
    private final List<String> organicLog;

    private int leftPos;
    private int topPos;
    private int panelH;
    private int view; // 0 = upgrade tree, 1 = reflex (emerald) log, 2 = organic log

    public BrainNodeScreen(int level, int maxLevel, int emeralds, int costNext,
                           int villagers, int guards, int golems, int fertilized, int organic,
                           List<String> reflexLog, List<String> organicLog) {
        super(Component.literal("Village Brain Node"));
        this.level = level;
        this.maxLevel = maxLevel;
        this.emeralds = emeralds;
        this.costNext = costNext;
        this.villagers = villagers;
        this.guards = guards;
        this.golems = golems;
        this.fertilized = fertilized;
        this.organic = organic;
        this.reflexLog = reflexLog;
        this.organicLog = organicLog;
    }

    /** The view the toggle button cycles to next (skips the organic tab when the organic system is off). */
    private int nextView(int v) {
        int n = (v + 1) % 3;
        if (n == 2 && !com.example.villagefightsback.Config.organicMassEnabled()) n = 0;
        return n;
    }

    private static String viewLabel(int v) {
        return v == 0 ? "Upgrades" : v == 1 ? "Reflex log" : "Organic log";
    }

    @Override
    protected void init() {
        this.panelH = HEADER_H + maxLevel * ROW_H + FOOTER_H;
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - panelH) / 2;
        // A header toggle that cycles Upgrades -> Reflex log -> Organic log (-> Upgrades). The button shows the
        // view it will switch TO. The organic tab is skipped when the organic system is off.
        addRenderableWidget(new Button(leftPos + PANEL_W - 78, topPos + 4, 72, 16,
                Component.literal(viewLabel(nextView(view))), b -> {
            view = nextView(view);
            b.setMessage(Component.literal(viewLabel(nextView(view))));
        }));
    }

    @Override
    public void render(PoseStack ps, int mouseX, int mouseY, float partial) {
        this.renderBackground(ps);

        // Panel background + border.
        this.fill(ps, leftPos - 1, topPos - 1, leftPos + PANEL_W + 1, topPos + panelH + 1, 0xFF1A1A1F);
        this.fill(ps, leftPos, topPos, leftPos + PANEL_W, topPos + panelH, 0xFF2A2A33);
        drawBorder(ps, leftPos, topPos, PANEL_W, panelH, 0xFF55556A);

        // Header.
        Component title = Component.literal("Village Brain Node").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        this.drawString(ps, font, title, leftPos + 8, topPos + 8, 0xFFFFFFFF);

        Upgrade hovered = null;

        if (view == 1) {
            renderLog(ps, "Reflex log (last 10)", reflexLog, "No reflexes yet.");
            super.render(ps, mouseX, mouseY, partial);
            return;
        }
        if (view == 2) {
            renderLog(ps, "Organic log (last 10)", organicLog, "No organic activity yet.");
            super.render(ps, mouseX, mouseY, partial);
            return;
        }

        // One row per level.
        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            int rowY = topPos + HEADER_H + (lvl - 1) * ROW_H;
            boolean unlocked = lvl <= level;

            // Alternating row strip for readability.
            if ((lvl & 1) == 0) this.fill(ps, leftPos + 2, rowY, leftPos + PANEL_W - 2, rowY + ROW_H - 2, 0x22000000);

            // Level label.
            int labelColor = unlocked ? 0xFFFFE060 : 0xFF707070;
            this.drawString(ps, font, "Lvl " + lvl, leftPos + 8, rowY + (ROW_H - 8) / 2, labelColor);

            List<Upgrade> ups = BrainNodeUpgrades.forLevel(lvl);
            if (ups.isEmpty()) {
                Component todo = Component.literal("(no upgrades defined yet)").withStyle(ChatFormatting.DARK_GRAY);
                this.drawString(ps, font, todo, leftPos + ICONS_X, rowY + (ROW_H - 8) / 2, 0xFF555555);
                continue;
            }

            int iconY = rowY + (ROW_H - ICON) / 2;
            for (int i = 0; i < ups.size(); i++) {
                Upgrade u = ups.get(i);
                int iconX = leftPos + ICONS_X + i * (ICON + ICON_GAP);
                if (u.entity() != null && GuiEntityIcons.render(ps, iconX, iconY, u.entity(), ENTITY_ICON_SCALE)) {
                    // drawn as a live, animated entity (e.g. the spinning wall defender)
                } else if (u.texture() != null) {
                    // 16x16 face/GUI texture: 1.19.2 blit binds the texture via the shader, not a ResourceLocation arg.
                    RenderSystem.setShader(GameRenderer::getPositionTexShader);
                    RenderSystem.setShaderTexture(0, u.texture());
                    this.blit(ps, iconX, iconY, 0, 0, ICON, ICON, ICON, ICON);
                } else {
                    Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(u.icon(), iconX, iconY);
                }
                if (!unlocked) {
                    this.fill(ps, iconX, iconY, iconX + ICON, iconY + ICON, 0xB0101010); // grey-out locked
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
        this.drawString(ps, font, progress, leftPos + 8, footerY, 0xFFFFFFFF);

        // Organic mass (1.1.2): a dark-green line under the emerald line when the organic system is on. The lines
        // below are pushed down by `extra` so nothing overlaps.
        boolean organicShown = com.example.villagefightsback.Config.organicMassEnabled();
        if (organicShown) {
            this.drawString(ps, font, Component.literal(organic + " organic mass").withStyle(ChatFormatting.DARK_GREEN),
                    leftPos + 8, footerY + 12, 0xFFFFFFFF);
        }
        int extra = organicShown ? 12 : 0;

        // Defenders currently within the brain node's radius, by type.
        Component counts = Component.literal("Villagers: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(villagers)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   Guards: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(guards)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   Golems: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(golems)).withStyle(ChatFormatting.WHITE));
        this.drawString(ps, font, counts, leftPos + 8, footerY + 12 + extra, 0xFFFFFFFF);

        // Fertilized blocks spread by the village (the green takeover footprint).
        Component fert = Component.literal("Fertilized blocks: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(fertilized)).withStyle(ChatFormatting.DARK_GREEN));
        this.drawString(ps, font, fert, leftPos + 8, footerY + 24 + extra, 0xFFFFFFFF);

        // Small light-gray footnote at the very bottom (rendered at a reduced scale), wrapped to the panel width.
        float helpScale = 0.72F;
        Component help = Component.literal(
                "Feed the brain node with emeralds to get Hero of the Village and be allowed to open village chests!")
                .withStyle(ChatFormatting.GRAY);
        int splitW = (int) ((PANEL_W - 16) / helpScale); // wider wrap to account for the smaller font
        int helpY = footerY + 40 + extra;
        ps.pushPose();
        ps.scale(helpScale, helpScale, 1.0F);
        for (net.minecraft.util.FormattedCharSequence line : font.split(help, splitW)) {
            // coordinates are in the scaled space, so divide the screen position by the scale
            this.drawString(ps, font, line, (int) ((leftPos + 8) / helpScale), (int) (helpY / helpScale), 0xFFBBBBBB);
            helpY += 8;
        }
        ps.popPose();

        super.render(ps, mouseX, mouseY, partial);

        // Tooltip last, on top of everything.
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(hovered.name());
            lines.add(hovered.desc());
            this.renderComponentTooltip(ps, lines, mouseX, mouseY);
        }
    }

    /** A log tab: the brain's last (up to 10) actions of one kind, newest at the top, with the day number. */
    private void renderLog(PoseStack ps, String heading, List<String> lines, String empty) {
        this.drawString(ps, font, Component.literal(heading).withStyle(ChatFormatting.AQUA),
                leftPos + 8, topPos + HEADER_H, 0xFFFFFFFF);
        int y = topPos + HEADER_H + 14;
        if (lines == null || lines.isEmpty()) {
            this.drawString(ps, font, Component.literal(empty).withStyle(ChatFormatting.DARK_GRAY),
                    leftPos + 8, y, 0xFF555555);
            return;
        }
        int wrapW = PANEL_W - 16; // wrap long lines (e.g. a marker on a long-named target) onto extra lines
        for (int i = lines.size() - 1; i >= 0; i--) { // newest first
            net.minecraft.network.chat.Component line = Component.literal(lines.get(i)).withStyle(ChatFormatting.GRAY);
            for (net.minecraft.util.FormattedCharSequence seq : font.split(line, wrapW)) {
                this.drawString(ps, font, seq, leftPos + 8, y, 0xFFFFFFFF);
                y += 10;
            }
        }
    }

    private static void drawBorder(PoseStack ps, int x, int y, int w, int h, int color) {
        GuiComponent.fill(ps, x, y, x + w, y + 1, color);
        GuiComponent.fill(ps, x, y + h - 1, x + w, y + h, color);
        GuiComponent.fill(ps, x, y, x + 1, y + h, color);
        GuiComponent.fill(ps, x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
