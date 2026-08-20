package com.example.villagefightsback.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.AllayModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.allay.Allay;

import org.joml.Matrix4f;

/**
 * The blue halo the Village Spirit carries.
 *
 * <p>It is the Ender Dragon's death glow, shrunk and recoloured. That effect is a burst of flat rays fanning
 * out from a point: each ray is three triangles running from a bright centre vertex to three tips whose alpha
 * is zero, so it reads as light rather than as geometry, drawn on {@link RenderType#lightning()} which needs no
 * texture and blends additively. Vanilla builds it in {@code EnderDragonRenderer}, where the helpers are
 * private, so the geometry is rebuilt here rather than borrowed.
 *
 * <p>Three things differ from the dragon:
 * <ul>
 *   <li><b>Blue, not white.</b> Only the centre vertex carries colour -- the tips are transparent, so the
 *       gradient does the work.</li>
 *   <li><b>Small.</b> The dragon's rays run 5 to 25 BLOCKS. An Allay is 0.35 wide, so these run about a block
 *       at most: an aura that hugs the spirit rather than a pillar of light visible across the map.</li>
 *   <li><b>Steady, not a one-shot.</b> The dragon's version grows over its 200-tick death. This one holds a
 *       fixed ray count and turns slowly, with a gentle pulse, so it can burn for the whole flight.</li>
 * </ul>
 *
 * <p>The ray directions come from a fixed seed, exactly as vanilla's do, so the halo is stable from frame to
 * frame instead of flickering into a new shape 60 times a second. Only the slow rotation and the pulse move.
 */
public class SpiritAuraLayer extends RenderLayer<Allay, AllayModel> {
    /** Same constant the dragon's ray geometry uses: the half-width of an equilateral triangle. */
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0) / 2.0);

    /** Fixed instead of growing -- this is an aura, not a death throe. */
    private static final int RAYS = 14;
    /** Ray length, in blocks. The dragon uses 5..25; a spirit is a third of a block wide. */
    private static final float LEN_MIN = 0.45F, LEN_VAR = 0.55F;
    /** Ray half-width. */
    private static final float WIDTH_MIN = 0.10F, WIDTH_VAR = 0.10F;
    /** Degrees per tick the whole halo turns. */
    private static final float SPIN = 0.9F;

    private static final int R = 90, G = 170, B = 255;   // the blue

    public SpiritAuraLayer(RenderLayerParent<Allay, AllayModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, Allay entity,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        // The founding spirit carries a village and glows for it. One bought to raise a single villager is
        // running an errand and looks like any other allay.
        if (entity instanceof com.example.villagefightsback.VillageSpiritEntity vs && vs.isErrand()) return;

        // Gentle breathing, so the halo is alive without strobing.
        float pulse = 0.55F + 0.25F * Mth.sin(ageInTicks * 0.15F);
        int alpha = (int) (255.0F * pulse);

        RandomSource rnd = RandomSource.create(432L);   // same seed vanilla uses; keeps the shape stable
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        pose.pushPose();
        // Layer space is the MODEL's, not the world's: LivingEntityRenderer has already applied
        // scale(-1,-1,1) and translate(0, -1.501, 0), so +Y points DOWN and the origin sits 1.501 above the
        // entity's feet. Height above the feet is therefore (1.501 - y), and the 0.55 this used to pass put the
        // halo 0.95 up -- a third of a block clear above the head of a mob only 0.6 tall, which is exactly the
        // offset that showed. 1.2 puts it at ~0.3, through the middle of the body.
        pose.translate(0.0F, 1.2F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(ageInTicks * SPIN));

        for (int i = 0; i < RAYS; i++) {
            // Vanilla stacks six random rotations per ray; the same trick scatters these evenly over a sphere.
            pose.mulPose(Axis.XP.rotationDegrees(rnd.nextFloat() * 360.0F));
            pose.mulPose(Axis.YP.rotationDegrees(rnd.nextFloat() * 360.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(rnd.nextFloat() * 360.0F));
            pose.mulPose(Axis.XP.rotationDegrees(rnd.nextFloat() * 360.0F));
            pose.mulPose(Axis.YP.rotationDegrees(rnd.nextFloat() * 360.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(rnd.nextFloat() * 360.0F));

            float len = LEN_MIN + rnd.nextFloat() * LEN_VAR;
            float wide = WIDTH_MIN + rnd.nextFloat() * WIDTH_VAR;
            Matrix4f m = pose.last().pose();

            centre(consumer, m, alpha);
            tipA(consumer, m, len, wide);
            tipB(consumer, m, len, wide);

            centre(consumer, m, alpha);
            tipB(consumer, m, len, wide);
            tipC(consumer, m, len, wide);

            centre(consumer, m, alpha);
            tipC(consumer, m, len, wide);
            tipA(consumer, m, len, wide);
        }
        pose.popPose();
    }

    /** The bright origin every ray fans out from -- the only vertex that carries colour. */
    private static void centre(VertexConsumer c, Matrix4f m, int alpha) {
        c.vertex(m, 0.0F, 0.0F, 0.0F).color(R, G, B, alpha).endVertex();
    }

    private static void tipA(VertexConsumer c, Matrix4f m, float len, float wide) {
        c.vertex(m, -HALF_SQRT_3 * wide, len, -0.5F * wide).color(R, G, B, 0).endVertex();
    }

    private static void tipB(VertexConsumer c, Matrix4f m, float len, float wide) {
        c.vertex(m, HALF_SQRT_3 * wide, len, -0.5F * wide).color(R, G, B, 0).endVertex();
    }

    private static void tipC(VertexConsumer c, Matrix4f m, float len, float wide) {
        c.vertex(m, 0.0F, len, 1.0F * wide).color(R, G, B, 0).endVertex();
    }
}
