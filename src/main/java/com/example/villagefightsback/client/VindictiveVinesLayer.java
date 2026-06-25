package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Overlays green vines on a Vindictive Gigachad, on TOP of its gold gigachad skin. The vine texture is mostly
 * transparent so only the vines draw. Grown slightly more than the gold shell (GoldGolemLayer = 1.02) so the
 * vines sit just outside it and stay visible.
 */
public class VindictiveVinesLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
    private static final ResourceLocation VINES =
            TheVillageFightsBack.id("textures/entity/vindictive_gigachad_vines.png");
    private static final float GROW = 1.03F; // > gold shell's 1.02 -> vines render over the gold

    public VindictiveVinesLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, IronGolem golem,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (golem.isInvisible()) return;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(VINES));
        pose.pushPose();
        pose.scale(GROW, GROW, GROW);
        getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
    }
}
