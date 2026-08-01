package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.WorldMode;
import com.example.villagefightsback.network.ChooseModePacket;
import com.example.villagefightsback.network.ModNetwork;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The one-off game-mode screen, shown over the spawn view the first time a player enters a freshly created
 * world. Full-bleed key art, the stylised mod logo, the question, and one vanilla button per mode.
 *
 * <p>It cannot be dismissed until a mode is picked (no ESC, no close button). Once one is, the buttons are
 * removed and replaced by the note about changing it later; the screen then closes on a click, on ESC, or by
 * itself after {@link #AUTO_CLOSE_TICKS}.
 *
 * <p>{@link #isPauseScreen()} deliberately returns false: in singleplayer a pausing screen also pauses the
 * integrated server, which would stop it ever processing the answer packet.
 */
public class ModeSelectScreen extends Screen {
    private static final ResourceLocation BG =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/gui/mode_select_bg.png");
    private static final ResourceLocation LOGO =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/gui/mode_select_title.png");

    /** Native sizes of the two textures above (needed by blit, and neither is a power of two). */
    private static final int BG_W = 1280, BG_H = 755;
    private static final int LOGO_W = 1024, LOGO_H = 298;

    private static final int BUTTON_W = 260;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;

    /** How long the confirmation stays up before the screen closes on its own (20 ticks = 1s). */
    private static final int AUTO_CLOSE_TICKS = 160;

    private static final String CMD_BLOCK = "/thevillagefightsback config enable_block_infection <true|false>";
    private static final String CMD_MOB = "/thevillagefightsback config enable_mob_infection <true|false>";

    private WorldMode picked;
    private int ticksLeft = AUTO_CLOSE_TICKS;

    /** Y of the top of the button column, shared with the confirmation text so the two occupy the same band. */
    private int contentY;
    private int questionY;

    public ModeSelectScreen() {
        super(Component.literal("The Village Fights Back"));
    }

    @Override
    protected void init() {
        // The logo is fitted into a box that is generous on width but capped on height, so a short window
        // shrinks it instead of pushing the buttons off the bottom.
        float scale = logoScale();
        int logoH = Math.round(LOGO_H * scale);
        int logoY = Math.max(4, Math.round(this.height * 0.06F));

        this.questionY = logoY + logoH + 12;
        this.contentY = questionY + 26;

        if (picked != null) return; // answered already: no buttons, just the note (see render)

        int x = (this.width - BUTTON_W) / 2;
        int y = contentY;
        for (WorldMode mode : WorldMode.values()) {
            addRenderableWidget(Button.builder(Component.literal(mode.title()), b -> choose(mode))
                    .bounds(x, y, BUTTON_W, BUTTON_H).build());
            y += BUTTON_H + BUTTON_GAP;
        }
    }

    /** Uniform scale that fits the logo inside 72% of the width and 26% of the height, never upscaling past 1. */
    private float logoScale() {
        float byWidth = (this.width * 0.72F) / LOGO_W;
        float byHeight = (this.height * 0.26F) / LOGO_H;
        return Math.min(1.0F, Math.min(byWidth, byHeight));
    }

    private void choose(WorldMode mode) {
        if (picked != null) return;
        picked = mode;
        ticksLeft = AUTO_CLOSE_TICKS;
        ModNetwork.CHANNEL.sendToServer(new ChooseModePacket(mode.id()));
        clearWidgets(); // the three buttons disappear; the note below takes their place
    }

    @Override
    public void tick() {
        if (picked != null && --ticksLeft <= 0) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderKeyArt(g);
        renderLogo(g);

        // The question, drawn a little larger than the standard font so it carries against the art.
        if (picked == null) {
            drawScaled(g, Component.literal("Which Game Mode?").withStyle(ChatFormatting.WHITE),
                    this.width / 2, questionY, 1.5F, 0xFFFFFFFF);
        } else {
            renderConfirmation(g);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    /** The key art, scaled to COVER the window (aspect preserved, overflow cropped by the viewport). */
    private void renderKeyArt(GuiGraphics g) {
        float scale = Math.max((float) this.width / BG_W, (float) this.height / BG_H);
        int w = Math.round(BG_W * scale);
        int h = Math.round(BG_H * scale);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        g.blit(BG, x, y, w, h, 0.0F, 0.0F, BG_W, BG_H, BG_W, BG_H);
    }

    /** The stylised mod logo, centred above the question. */
    private void renderLogo(GuiGraphics g) {
        float scale = logoScale();
        int w = Math.round(LOGO_W * scale);
        int h = Math.round(LOGO_H * scale);
        int x = (this.width - w) / 2;
        int y = Math.max(4, Math.round(this.height * 0.06F));
        // The logo has a soft alpha edge, so blending has to be on. Left on afterwards: that is the normal state
        // for GUI rendering and what the widgets drawn after us expect.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(LOGO, x, y, w, h, 0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
    }

    /** What replaces the buttons once a mode is picked. */
    private void renderConfirmation(GuiGraphics g) {
        Component mode = Component.literal(picked.title()).withStyle(ChatFormatting.GREEN);
        Component intro = Component.literal("You can change it anytime using").withStyle(ChatFormatting.WHITE);
        Component cmd1 = Component.literal(CMD_BLOCK).withStyle(ChatFormatting.YELLOW);
        Component cmd2 = Component.literal(CMD_MOB).withStyle(ChatFormatting.YELLOW);
        Component luck = Component.literal("Good luck!").withStyle(ChatFormatting.WHITE);

        // The command lines are long. Shrink the whole block rather than let it run off a narrow window (which is
        // what a small window at GUI scale 3-4 amounts to).
        int widest = Math.max(Math.max(font.width(cmd1), font.width(cmd2)), font.width(intro));
        float fit = Math.min(1.0F, (this.width - 24.0F) / Math.max(1, widest));
        float modeScale = Math.min(1.3F, (this.width - 24.0F) / Math.max(1, font.width(mode)));

        int lineH = Math.max(1, Math.round((font.lineHeight + 3) * fit));
        int modeH = Math.round((font.lineHeight + 3) * modeScale);
        int top = contentY;
        int blockH = modeH + 5 + lineH * 4 + 10;
        int half = Math.round(Math.max(widest * fit, font.width(mode) * modeScale)) / 2 + 10;

        // A dark plate so the commands stay legible over the bright sky behind them.
        g.fill(this.width / 2 - half, top - 6, this.width / 2 + half, top + blockH - 6, 0xB0000000);

        int y = top;
        drawScaled(g, mode, this.width / 2, y, modeScale, 0xFFFFFFFF);
        y += modeH + 5;
        drawScaled(g, intro, this.width / 2, y, fit, 0xFFFFFFFF);
        y += lineH;
        drawScaled(g, cmd1, this.width / 2, y, fit, 0xFFFFFFFF);
        y += lineH;
        drawScaled(g, cmd2, this.width / 2, y, fit, 0xFFFFFFFF);
        y += lineH;
        drawScaled(g, luck, this.width / 2, y, fit, 0xFFFFFFFF);

        drawScaled(g, Component.literal("click to continue").withStyle(ChatFormatting.GRAY),
                this.width / 2, top + blockH + 2, 0.8F * fit, 0xFFFFFFFF);
    }

    /** Centred text at an arbitrary scale (the font itself only draws at 1x). */
    private void drawScaled(GuiGraphics g, Component text, int centerX, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, Math.round(centerX / scale) - font.width(text) / 2,
                Math.round(y / scale), color, true);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picked != null) { onClose(); return true; } // the note is up: any click dismisses it
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** No escaping the question; once it is answered ESC closes the note like any other screen. */
    @Override
    public boolean shouldCloseOnEsc() {
        return picked != null;
    }

    /** See the class note: pausing here would also pause the integrated server, which has to handle the answer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
