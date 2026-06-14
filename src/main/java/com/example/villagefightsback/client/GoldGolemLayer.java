package com.example.villagefightsback.client;

import com.example.villagefightsback.ModItems;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Re-skins a STAGE-3 (upgraded) iron golem with a fancy metallic-gold texture. We can't read the server-side
 * upgrade flag on the client (it isn't synced), so -- like {@link HeldWeaponLayer} -- we use the held item as the
 * proxy: an upgraded golem holds our rocket launcher, a normal one's hand is empty.
 *
 * The gold model is drawn at the same animated pose as the base golem but scaled up a hair so its opaque
 * surface sits just outside the iron skin and fully hides it (no z-fighting). The held rocket launcher sticks
 * out past the body, so it stays visible.
 */
public class GoldGolemLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
    private static final ResourceLocation GOLD =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/gold_golem.png");
    private static final float GROW = 1.02F; // 2% larger -> opaque gold shell covers the iron underneath

    public GoldGolemLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, IronGolem golem,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (golem.isInvisible()) return;
        if (golem.getMainHandItem().getItem() != ModItems.ROCKET_LAUNCHER.get()) return; // only upgraded golems

        VertexConsumer vc = buffer.getBuffer(getParentModel().renderType(GOLD));
        pose.pushPose();
        pose.scale(GROW, GROW, GROW);
        // The parent model is already in this tick's animation pose, so just re-draw it in gold.
        getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
