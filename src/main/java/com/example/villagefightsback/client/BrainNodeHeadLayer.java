package com.example.villagefightsback.client;

import com.example.villagefightsback.BrainNodeWardenEntity;
import com.example.villagefightsback.ModBlocks;
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
 * Renders the Village Brain Node block where the Warden's head would be. The renderer hides the real head
 * (+ tendrils); this layer follows the animated head bone so the block bobs and looks around with the body.
 * SCALE / DX-DY-DZ are visual tunables (model space is in block units but flipped y/z by the renderer).
 */
public class BrainNodeHeadLayer extends RenderLayer<BrainNodeWardenEntity, WardenModel<BrainNodeWardenEntity>> {
    private static final float SCALE = 0.7F;    // 1.05 / 1.5 boss model scale -> head keeps its absolute size
    private static final float DX = -0.5F;      // centre horizontally on the bone
    private static final float DY = -0.15F;     // height of the block over the bone (less negative = higher)
    private static final float DZ = -0.5F;

    private final BlockRenderDispatcher blocks;

    public BrainNodeHeadLayer(RenderLayerParent<BrainNodeWardenEntity, WardenModel<BrainNodeWardenEntity>> parent,
                              BlockRenderDispatcher blocks) {
        super(parent);
        this.blocks = blocks;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, BrainNodeWardenEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float age,
                       float netHeadYaw, float headPitch) {
        BlockState state = ModBlocks.VILLAGE_BRAIN_NODE.get().defaultBlockState();
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
