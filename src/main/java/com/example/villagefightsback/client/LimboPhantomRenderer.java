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

/** Renders the Limbo Phantom: a 3x black-and-white phantom with white vines and glowing white eyes. */
public class LimboPhantomRenderer extends MobRenderer<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_phantom.png");
    private static final float SCALE = 3.0F;

    public LimboPhantomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PhantomModel<>(ctx.bakeLayer(ModelLayers.PHANTOM)), 1.5F);
        addLayer(new WhiteEyesLayer(this));
        addLayer(new WhiteVinesLayer(this));
    }

    @Override
    protected void scale(LimboPhantomEntity entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(LimboPhantomEntity entity) {
        return TEXTURE;
    }

    /** Fullbright white eyes -- so they glow even from far away / in the dark. */
    private static class WhiteEyesLayer extends EyesLayer<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> {
        private static final RenderType EYES = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_phantom_eyes.png"));

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
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_phantom_vines.png");

        WhiteVinesLayer(RenderLayerParent<LimboPhantomEntity, PhantomModel<LimboPhantomEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, LimboPhantomEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) return;
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(VINES));
            getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
