package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboGravelBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;

import org.joml.Matrix4f;

/**
 * Makes a &gt;150 HP "unstable" Limbo gravel GLOW purple: an additive purple shell drawn over the whole block
 * whose intensity pulses, faster and faster, as the self-destruct deadline (stored on the block entity) nears --
 * a visible countdown. Stable gravel (deadline 0) draws nothing. Additive ({@link RenderType#lightning()}), so
 * it reads as light rather than paint, and is fully client-side (no light-engine churn).
 */
public class LimboGravelRenderer implements BlockEntityRenderer<LimboGravelBlockEntity> {

    /** The unstable countdown's full length, in ticks (matches {@code LimboManager.UNSTABLE_TICKS} = 30s). */
    private static final double TOTAL = 600.0;

    // A unit cube slightly inflated so the glow wraps the gravel; 8 corners + 6 faces.
    private static final float LO = -0.02F, HI = 1.02F;
    private static final float[][] C = {
            {LO, LO, LO}, {HI, LO, LO}, {HI, LO, HI}, {LO, LO, HI}, // bottom 0..3
            {LO, HI, LO}, {HI, HI, LO}, {HI, HI, HI}, {LO, HI, HI}, // top    4..7
    };
    private static final int[][] FACES = {
            {0, 1, 2, 3}, {4, 7, 6, 5}, {0, 4, 5, 1}, {3, 2, 6, 7}, {0, 3, 7, 4}, {1, 5, 6, 2},
    };

    public LimboGravelRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LimboGravelBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        long until = be.unstableUntil();
        if (until <= 0L) return;
        Level level = be.getLevel();
        if (level == null) return;
        double remaining = until - (level.getGameTime() + partialTick);
        if (remaining <= 0) return;
        // elapsed = time since the gravel was placed (0 -> TOTAL as the deadline nears). Driving the pulse off
        // elapsed (a small, smoothly-growing number) -- NOT the huge absolute game time -- avoids aliasing.
        double elapsed = TOTAL - remaining;
        if (elapsed < 0) elapsed = 0;
        // The flash RATE ramps linearly from slow (1/F_START_TICKS) to fast (1/F_END_TICKS); the phase is the
        // integral of that rate, so the glow starts pulsing lazily and accelerates smoothly to a frantic blink.
        double fStart = 1.0 / 26.0, fEnd = 1.0 / 2.0;
        double phase = fStart * elapsed + (fEnd - fStart) * elapsed * elapsed / (2.0 * TOTAL);
        float intensity = (float) (0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI)); // smooth 0..1 pulse
        float a = 0.08F + 0.6F * intensity;                                      // near-dark between flashes

        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        for (int[] f : FACES) {
            for (int i = 0; i < 4; i++) vert(vc, m, C[f[i]], a);
            for (int i = 3; i >= 0; i--) vert(vc, m, C[f[i]], a); // reverse winding too -> visible from any side
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float[] p, float a) {
        vc.vertex(m, p[0], p[1], p[2]).color(0.62F, 0.13F, 0.94F, a).endVertex();
    }
}
