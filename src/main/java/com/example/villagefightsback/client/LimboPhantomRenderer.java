package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboPhantomEntity;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders the Limbo Phantom: a 3x black-and-white phantom with white vines and glowing white eyes. The model
 *  is scaled a fixed 3x to match the baked 2.7x1.5 hitbox (3x the vanilla phantom box), so the body fills its
 *  hitbox and stays easy to hit. */
public class LimboPhantomRenderer extends MobRenderer<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/limbo_phantom.png");

    public LimboPhantomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PhantomModel<>(ctx.bakeLayer(ModelLayers.PHANTOM)), 0.75F);
        addLayer(new WhiteEyesLayer(this));
        addLayer(new WhiteVinesLayer(this));
    }

    @Override
    protected void scale(LimboPhantomEntity entity, PoseStack pose, float partialTick) {
        pose.scale(3.0F, 3.0F, 3.0F); // fixed 3x, matching the baked 2.7x1.5 hitbox (3x the vanilla phantom box)
        // Vanilla's PhantomRenderer.scale ends with exactly this translate, and overriding scale() dropped it --
        // which is why the body floated above its own hitbox. The phantom model is authored off-origin and this
        // is the compensation for it; the numbers are vanilla's, applied after the scale as vanilla applies them.
        pose.translate(0.0F, 1.3125F, 0.1875F);
    }

    @Override
    protected void setupRotations(LimboPhantomEntity entity, PoseStack pose, float ageInTicks, float yBodyRot,
                                  float partialTicks, float scale) {
        super.setupRotations(entity, pose, ageInTicks, yBodyRot, partialTicks, scale);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(entity.getXRot())); // pitch like a diving phantom
    }

    @Override
    public ResourceLocation getTextureLocation(LimboPhantomEntity entity) {
        return TEXTURE;
    }

    /** Fullbright white eyes -- so they glow even from far away / in the dark. */
    private static class WhiteEyesLayer extends EyesLayer<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> {
        private static final RenderType EYES = RenderType.eyes(
                TheVillageFightsBack.id("textures/entity/limbo_phantom_eyes.png"));

        WhiteEyesLayer(RenderLayerParent<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }

    /** White vines overlaid on the body (mostly-transparent vine texture, grown slightly so it sits outside). */
    private static class WhiteVinesLayer extends RenderLayer<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> {
        private static final ResourceLocation VINES =
                TheVillageFightsBack.id("textures/entity/limbo_phantom_vines.png");

        WhiteVinesLayer(RenderLayerParent<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, LimboPhantomEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) return;
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(VINES));
            getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
