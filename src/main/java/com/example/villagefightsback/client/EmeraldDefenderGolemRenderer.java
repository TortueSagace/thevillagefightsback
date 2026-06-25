package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Renders the Emerald Defender Golem: like the {@link DefenderGolemRenderer} (an iron golem body with the iron
 * head hidden and a Wall Defender drawn in its place) EXCEPT the BODY uses the emeralized iron-golem texture +
 * the shared emerald speckle glow, and the Wall Defender HEAD is tinted green and rendered full-bright so it
 * glows in the dark (the {@code emerald} mode of {@link DefenderGolemHeadLayer}). It gets NO head spike.
 *
 * <p>It extends {@link IronGolemRenderer} directly (not {@code DefenderGolemRenderer}) so it does NOT inherit
 * the plain white head layer -- it adds the green/glowing variant instead.
 */
public class EmeraldDefenderGolemRenderer extends IronGolemRenderer {
    private static final ResourceLocation BODY =
            TheVillageFightsBack.id("textures/entity/emerald_iron_golem.png");

    public EmeraldDefenderGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new DefenderGolemHeadLayer(this,
                ctx.bakeLayer(TheVillageFightsBackClient.WALL_DEFENDER_LAYER),
                ctx.getBlockRenderDispatcher(), true));            // green + full-bright (glow-in-the-dark) head
        addLayer(new EmeraldGlowLayer<>(this, "emerald_iron_golem")); // emissive body speckle glow
    }

    @Override
    public void render(IronGolem entity, float yaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        // Hide the iron head -- the head layer stands in for it (the head bone still animates).
        ModelPart head = getModel().root().getChild("head");
        boolean prev = head.visible;
        head.visible = false;
        super.render(entity, yaw, partialTicks, pose, buffer, light);
        head.visible = prev;
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return BODY;
    }
}
