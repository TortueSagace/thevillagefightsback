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
 * <p>It cannot be dismissed until a mode is picked (no ESC, no close button). Picking one closes the screen
 * immediately and drops the player straight into the world -- the server acknowledges the choice in chat
 * (see {@link com.example.villagefightsback.network.ChooseModePacket}), so no follow-up screen is needed.
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
    private static final int LOGO_W = 1276, LOGO_H = 313;

    private static final int BUTTON_W = 260;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;

    /** Set the instant a mode is picked, so a double click cannot send the answer twice. */
    private boolean answered;

    /** Y of the top of the button column. */
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

    /** Send the answer and get out of the way at once: the server confirms the mode in chat. */
    private void choose(WorldMode mode) {
        if (answered) return;
        answered = true;
        ModNetwork.CHANNEL.sendToServer(new ChooseModePacket(mode.id()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderKeyArt(g);
        renderLogo(g);

        // The question, drawn a little larger than the standard font so it carries against the art.
        drawScaled(g, Component.literal("Which Game Mode?").withStyle(ChatFormatting.WHITE),
                this.width / 2, questionY, 1.5F, 0xFFFFFFFF);

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

    /** Centred text at an arbitrary scale (the font itself only draws at 1x). */
    private void drawScaled(GuiGraphics g, Component text, int centerX, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, Math.round(centerX / scale) - font.width(text) / 2,
                Math.round(y / scale), color, true);
        g.pose().popPose();
    }

    /** No escaping the question: the only way out is to pick a mode, which closes the screen itself. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** See the class note: pausing here would also pause the integrated server, which has to handle the answer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
