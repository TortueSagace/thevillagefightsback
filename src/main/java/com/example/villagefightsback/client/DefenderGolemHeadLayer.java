package com.example.villagefightsback.client;

import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Draws the Defender Golem's head: the (reused) Wall Defender spinning conduit core sealed inside a 1x1
 * protecting-glass cocoon, anchored to the iron golem's head bone. {@link DefenderGolemRenderer} hides the
 * iron head; this layer stands in for it -- the same head-replacement trick the Vindictive Village Spirit
 * uses ({@link BrainNodeHeadLayer}).
 *
 * SCALE / D* are visual tunables (model space is in block units, with y/z flipped by the entity renderer).
 */
public class DefenderGolemHeadLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
    private static final ResourceLocation WALL_DEFENDER_TEX =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/wall_defender.png");

    // --- visual tunables (tweak in-game) ---
    private static final float GLASS_SCALE = 0.78F;  // protecting-glass cocoon ~0.78 blocks
    private static final float GLASS_DX = -0.5F;     // centre the unit block on the head bone
    // Centre the cocoon on the SAME point as the conduit core: the glass centre in head-bone space is
    // -GLASS_SCALE*(GLASS_DY+0.5); set it to the core's CORE_DY (-0.42) so glass + core are superposed.
    // (Solving -0.78*(GLASS_DY+0.5) = -0.42 -> GLASS_DY = 0.04. Higher = more positive here.)
    private static final float GLASS_DY = 0.04F;     // raised so the cocoon shares the core/head centre
    private static final float GLASS_DZ = -0.54F;    // match the core's small +z so centres align in z too
    private static final float CORE_SCALE = 1.15F;   // the conduit core inside the cocoon
    private static final float CORE_DY = -0.42F;     // raise the core (model y is down) toward the cocoon centre
    private static final float CORE_DZ = 0.03F;

    private final WallDefenderModel core;
    private final BlockRenderDispatcher blocks;

    public DefenderGolemHeadLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent,
                                  ModelPart wallDefenderRoot, BlockRenderDispatcher blocks) {
        super(parent);
        this.core = new WallDefenderModel(wallDefenderRoot);
        this.blocks = blocks;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, IronGolem golem,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (golem.isInvisible()) return;
        ModelPart head = getParentModel().root().getChild("head");
        pose.pushPose();
        head.translateAndRotate(pose); // follow the animated head bone (the iron head is hidden)

        // The conduit core (reused wall defender model), spinning, drawn in the model's (flipped) space.
        pose.pushPose();
        pose.translate(0.0F, CORE_DY, CORE_DZ);
        pose.scale(CORE_SCALE, CORE_SCALE, CORE_SCALE);
        core.setupHeadAnim(ageInTicks);
        VertexConsumer vc = buffer.getBuffer(core.renderType(WALL_DEFENDER_TEX));
        core.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();

        // The protecting-glass cocoon around it: flip y/z back upright, scale to cocoon size, centre on the bone.
        pose.pushPose();
        pose.scale(GLASS_SCALE, -GLASS_SCALE, -GLASS_SCALE);
        pose.translate(GLASS_DX, GLASS_DY, GLASS_DZ);
        blocks.renderSingleBlock(ModBlocks.PROTECTING_GLASS.get().defaultBlockState(), pose, buffer, light,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();

        pose.popPose();
    }
}
