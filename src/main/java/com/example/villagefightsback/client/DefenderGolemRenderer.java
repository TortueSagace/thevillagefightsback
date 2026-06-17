package com.example.villagefightsback.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Renders a Defender Golem: a vanilla iron golem body with the iron head hidden and the
 * {@link DefenderGolemHeadLayer} (a wall defender sealed in a protecting-glass cocoon) drawn in its place.
 * Same head-replacement pattern the Vindictive Village Spirit uses ({@link BrainNodeWardenRenderer}).
 */
public class DefenderGolemRenderer extends IronGolemRenderer {
    public DefenderGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new DefenderGolemHeadLayer(this,
                ctx.bakeLayer(TheVillageFightsBackClient.WALL_DEFENDER_LAYER),
                ctx.getBlockRenderDispatcher()));
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
}
