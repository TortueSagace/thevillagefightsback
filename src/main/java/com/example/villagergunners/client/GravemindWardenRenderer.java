package com.example.villagergunners.client;

import com.example.villagergunners.GravemindWardenEntity;
import com.example.villagergunners.VillagerGunners;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the {@link GravemindWardenEntity}: the vanilla Warden model wearing our green texture, with the
 * real head hidden and the Village Gravemind block drawn in its place ({@link GravemindHeadLayer}).
 */
public class GravemindWardenRenderer extends MobRenderer<GravemindWardenEntity, WardenModel<GravemindWardenEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(VillagerGunners.MODID, "textures/entity/gravemind_warden/warden.png");

    public GravemindWardenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WardenModel<>(ctx.bakeLayer(ModelLayers.WARDEN)), 0.9F);
        addLayer(new GravemindHeadLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    public ResourceLocation getTextureLocation(GravemindWardenEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(GravemindWardenEntity entity, float yaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        // Hide the Warden's own head (+ its tendrils) -- the gravemind block stands in for it. translateAndRotate
        // in the head layer still works on an invisible bone, so the block tracks the head's animation.
        ModelPart head = getModel().root().getChild("bone").getChild("head");
        boolean prev = head.visible;
        head.visible = false;
        super.render(entity, yaw, partialTicks, pose, buffer, light);
        head.visible = prev;
    }
}
