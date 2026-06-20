package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The animated EMERALD GLOW: a fullbright, frame-cycled "salt and pepper" overlay -- the entity-side equivalent of
 * the fertile blocks' twinkling specks. Entity textures cannot animate through .mcmeta, so the glow is baked into
 * {@value #FRAMES} discrete frame textures ({@code emerald_<mob>_glow0.png}..{@code glow7.png}) and this layer
 * swaps between them over time, rendering each emissively via {@link RenderType#eyes}. Frame phase is offset per
 * entity so a herd of emerald mobs doesn't pulse in lockstep.
 */
public class EmeraldGlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public static final int FRAMES = 8;
    private static final int FRAMETIME = 3; // ticks per frame (~1.2s loop)

    private final RenderType[] frames;

    public EmeraldGlowLayer(RenderLayerParent<T, M> parent, String baseName) {
        super(parent);
        this.frames = new RenderType[FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            this.frames[i] = RenderType.eyes(new ResourceLocation(
                    TheVillageFightsBack.MODID, "textures/entity/" + baseName + "_glow" + i + ".png"));
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        int frame = Math.floorMod((entity.tickCount + entity.getId() * 7) / FRAMETIME, FRAMES);
        VertexConsumer vc = buffer.getBuffer(this.frames[frame]);
        this.getParentModel().renderToBuffer(poseStack, vc, 15728640, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
