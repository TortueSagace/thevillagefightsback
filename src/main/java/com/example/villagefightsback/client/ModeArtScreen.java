package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
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
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/gui/mode_select_bg.png");
    protected static final ResourceLocation LOGO =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/gui/mode_select_title.png");

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
     * The key art, scaled to COVER the window (aspect preserved, overflow cropped by the viewport).
     *
     * <p>1.19.2 has no GuiGraphics, so the texture is not an argument to blit: it is bound on the shader first,
     * the way {@link BrainNodeScreen} already does it on this branch. The remaining arguments are identical to
     * the 1.20.1 GuiGraphics overload, which takes the same (x, y, w, h, u, v, uw, vh, texW, texH) after the
     * ResourceLocation, so the sampling and the placement are unchanged.
     */
    protected void renderKeyArt(PoseStack ps) {
        float scale = Math.max((float) this.width / BG_W, (float) this.height / BG_H);
        int w = Math.round(BG_W * scale);
        int h = Math.round(BG_H * scale);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        blit(ps, (this.width - w) / 2, (this.height - h) / 2, w, h, 0.0F, 0.0F, BG_W, BG_H, BG_W, BG_H);
    }

    /** The stylised mod logo, centred at the top. */
    protected void renderLogo(PoseStack ps) {
        float scale = logoScale();
        int w = Math.round(LOGO_W * scale);
        int h = Math.round(LOGO_H * scale);
        // The logo has a soft alpha edge, so blending has to be on. Left on afterwards: that is the normal state
        // for GUI rendering and what the widgets drawn after us expect.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, LOGO);
        blit(ps, (this.width - w) / 2, logoY(), w, h, 0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
    }

    /**
     * Centred text at an arbitrary scale (the font itself only draws at 1x).
     *
     * <p>GuiComponent#drawString always draws WITH a shadow, which is what the 1.20.1 call asked for by passing
     * dropShadow=true, so the look carries over exactly.
     */
    protected void drawScaled(PoseStack ps, Component text, int centerX, int y, float scale, int color) {
        ps.pushPose();
        ps.scale(scale, scale, 1.0F);
        drawString(ps, font, text, Math.round(centerX / scale) - font.width(text) / 2,
                Math.round(y / scale), color);
        ps.popPose();
    }

    /** Centred pre-wrapped line at an arbitrary scale, preserving the per-word styling of the source component. */
    protected void drawScaled(PoseStack ps, FormattedCharSequence line, int centerX, int y, float scale, int color) {
        ps.pushPose();
        ps.scale(scale, scale, 1.0F);
        drawString(ps, font, line, Math.round(centerX / scale) - font.width(line) / 2,
                Math.round(y / scale), color);
        ps.popPose();
    }

    /** See the class note: pausing would also pause the integrated server, which has to handle the answer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
