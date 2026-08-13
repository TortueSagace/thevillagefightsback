package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared furniture for the two world-creation screens ({@link ModeSelectScreen}, then {@link ThankYouScreen}):
 * the full-bleed key art, the stylised logo, and centred text at an arbitrary scale. Both screens show the same
 * backdrop, so the player experiences one continuous panel with the content swapping over it.
 *
 * <p>{@link #isPauseScreen()} deliberately returns false for BOTH screens: in singleplayer a pausing screen also
 * pauses the integrated server, which would stop it ever processing the mode-choice packet.
 */
public abstract class ModeArtScreen extends Screen {
    protected static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(TheVillageFightsBack.MODID, "textures/gui/mode_select_bg.png");
    protected static final ResourceLocation LOGO =
            ResourceLocation.fromNamespaceAndPath(TheVillageFightsBack.MODID, "textures/gui/mode_select_title.png");

    /** Native sizes of the two textures above (needed by blit, and neither is a power of two). */
    protected static final int BG_W = 1280, BG_H = 755;
    protected static final int LOGO_W = 1276, LOGO_H = 313;

    protected ModeArtScreen(Component title) {
        super(title);
    }

    /** Uniform scale that fits the logo inside 72% of the width and 26% of the height, never upscaling past 1. */
    protected float logoScale() {
        float byWidth = (this.width * 0.72F) / LOGO_W;
        float byHeight = (this.height * 0.26F) / LOGO_H;
        return Math.min(1.0F, Math.min(byWidth, byHeight));
    }

    /** Y of the top of the logo. */
    protected int logoY() {
        return Math.max(4, Math.round(this.height * 0.06F));
    }

    /** Y just under the logo, where a screen's own content starts. */
    protected int logoBottom() {
        return logoY() + Math.round(LOGO_H * logoScale());
    }

    /**
     * These screens paint their own full-bleed background, so vanilla's must not run.
     *
     * <p>1.21 moved the background pass INSIDE {@code Screen.render}: it now calls {@code renderBackground}
     * (a blur pass plus the translucent in-world menu tile) and only then draws the widgets, where 1.20.1's
     * {@code Screen.render} drew widgets alone. Both subclasses paint the key art, the logo and their text
     * FIRST and call {@code super.render} last, exactly as they did on main -- so left alone, 1.21 would dim
     * and blur the whole panel the instant the buttons were drawn. Overriding this to do nothing restores
     * main's paint order (art, logo, text, widgets) without touching either subclass.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Intentionally empty: renderKeyArt below is this screen's background, drawn at the top of render().
    }

    /** The key art, scaled to COVER the window (aspect preserved, overflow cropped by the viewport). */
    protected void renderKeyArt(GuiGraphics g) {
        float scale = Math.max((float) this.width / BG_W, (float) this.height / BG_H);
        int w = Math.round(BG_W * scale);
        int h = Math.round(BG_H * scale);
        g.blit(BG, (this.width - w) / 2, (this.height - h) / 2, w, h, 0.0F, 0.0F, BG_W, BG_H, BG_W, BG_H);
    }

    /** The stylised mod logo, centred at the top. */
    protected void renderLogo(GuiGraphics g) {
        float scale = logoScale();
        int w = Math.round(LOGO_W * scale);
        int h = Math.round(LOGO_H * scale);
        // The logo has a soft alpha edge, so blending has to be on. Left on afterwards: that is the normal state
        // for GUI rendering and what the widgets drawn after us expect.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(LOGO, (this.width - w) / 2, logoY(), w, h, 0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
    }

    /** Centred text at an arbitrary scale (the font itself only draws at 1x). */
    protected void drawScaled(GuiGraphics g, Component text, int centerX, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, Math.round(centerX / scale) - font.width(text) / 2,
                Math.round(y / scale), color, true);
        g.pose().popPose();
    }

    /** Centred pre-wrapped line at an arbitrary scale, preserving the per-word styling of the source component. */
    protected void drawScaled(GuiGraphics g, FormattedCharSequence line, int centerX, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, line, Math.round(centerX / scale) - font.width(line) / 2,
                Math.round(y / scale), color, true);
        g.pose().popPose();
    }

    /** See the class note: pausing would also pause the integrated server, which has to handle the answer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
