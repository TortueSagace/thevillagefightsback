package com.example.villagergunners.client;

import com.example.villagergunners.GravemindWardenEntity;
import com.example.villagergunners.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the Village Gravemind block where the Warden's head would be. The renderer hides the real head
 * (+ tendrils); this layer follows the animated head bone so the block bobs and looks around with the body.
 * SCALE / DX-DY-DZ are visual tunables (model space is in block units but flipped y/z by the renderer).
 */
public class GravemindHeadLayer extends RenderLayer<GravemindWardenEntity, WardenModel<GravemindWardenEntity>> {
    private static final float SCALE = 1.05F;   // gravemind block ~ head-sized
    private static final float DX = -0.5F;      // centre horizontally on the bone
    private static final float DY = -1.05F;     // raise to sit where the head was (model space is y-flipped)
    private static final float DZ = -0.5F;

    private final BlockRenderDispatcher blocks;

    public GravemindHeadLayer(RenderLayerParent<GravemindWardenEntity, WardenModel<GravemindWardenEntity>> parent,
                              BlockRenderDispatcher blocks) {
        super(parent);
        this.blocks = blocks;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, GravemindWardenEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float age,
                       float netHeadYaw, float headPitch) {
        BlockState state = ModBlocks.VILLAGE_GRAVEMIND.get().defaultBlockState();
        // Warden hierarchy is root -> "bone" -> "body" -> "head"; walk the whole chain to the head pivot.
        ModelPart bone = getParentModel().root().getChild("bone");
        ModelPart body = bone.getChild("body");
        ModelPart head = body.getChild("head");
        pose.pushPose();
        bone.translateAndRotate(pose);
        body.translateAndRotate(pose);
        head.translateAndRotate(pose);          // now at the animated head pivot
        // The model is rendered y-down (the renderer flipped it); flip back so the block is upright, scale it
        // to head size, then centre it on the bone.
        pose.scale(SCALE, -SCALE, -SCALE);
        pose.translate(DX, DY, DZ);
        blocks.renderSingleBlock(state, pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
