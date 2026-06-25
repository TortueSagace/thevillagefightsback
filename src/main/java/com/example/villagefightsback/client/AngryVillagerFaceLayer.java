package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only: overlays an angry villager face on a vanilla {@link Villager} while it is FIGHTING (see
 * {@link AngryFaceClient} -- the fight signal is the synced arm-swing animation). The overlay texture is
 * laid out on the villager head UV and is fully transparent except over the face, so re-drawing the whole
 * parent model with it paints only the scowl over whatever the villager's real face is.
 *
 * Mirrors {@link VindictiveVinesLayer} / {@link GoldGolemLayer}: build a VertexConsumer for the overlay
 * texture's RenderType, then re-render the already-posed parent model into it. The model is grown a hair
 * so the overlay sits just outside the head and does not z-fight with the underlying face.
 */
@OnlyIn(Dist.CLIENT)
public class AngryVillagerFaceLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private static final ResourceLocation FACE =
            TheVillageFightsBack.id("textures/entity/angry_villager_face.png");
    private static final float GROW = 1.01F; // tiny outset so the scowl sits over (not inside) the face

    public AngryVillagerFaceLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, Villager entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        if (!AngryFaceClient.isAngry(entity)) return;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(FACE));
        pose.pushPose();
        pose.scale(GROW, GROW, GROW);
        getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
    }
}
