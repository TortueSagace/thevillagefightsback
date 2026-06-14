package com.example.villagefightsback.client;

import com.example.villagefightsback.BrainNodeWardenEntity;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the {@link BrainNodeWardenEntity}: the vanilla Warden model wearing our green texture, with the
 * real head hidden and the Village Brain Node block drawn in its place ({@link BrainNodeHeadLayer}).
 */
public class BrainNodeWardenRenderer extends MobRenderer<BrainNodeWardenEntity, WardenModel<BrainNodeWardenEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/brain_node_warden/warden.png");

    public BrainNodeWardenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WardenModel<>(ctx.bakeLayer(ModelLayers.WARDEN)), 0.9F);
        addLayer(new BrainNodeHeadLayer(this, ctx.getBlockRenderDispatcher()));
    }

    @Override
    public ResourceLocation getTextureLocation(BrainNodeWardenEntity entity) {
        return TEXTURE;
    }

    /** The boss is bigger -- a taller body. The brainNode-block head keeps its absolute size (the head layer
     *  counter-scales by 1/MODEL_SCALE), so it ends up relatively smaller than on a normal warden. */
    static final float BOSS_SCALE = 1.5F;

    @Override
    protected void scale(BrainNodeWardenEntity entity, PoseStack pose, float partialTick) {
        if (entity instanceof com.example.villagefightsback.UnleashedBrainNodeEntity) {
            pose.scale(BOSS_SCALE, BOSS_SCALE, BOSS_SCALE);
        }
    }

    @Override
    public void render(BrainNodeWardenEntity entity, float yaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        // Hide the Warden's own head (+ its tendrils) -- the brain node block stands in for it. translateAndRotate
        // in the head layer still works on an invisible bone, so the block tracks the head's animation.
        // Warden hierarchy is root -> "bone" -> "body" -> "head" (the head is under body, not bone).
        ModelPart head = getModel().root().getChild("bone").getChild("body").getChild("head");
        boolean prev = head.visible;
        head.visible = false;
        super.render(entity, yaw, partialTicks, pose, buffer, light);
        head.visible = prev;
    }
}
