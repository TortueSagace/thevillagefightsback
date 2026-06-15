package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Vex;

/** Limbo Vex: the vanilla vex renderer, re-skinned black-and-white, scaled 2x to match the baked hitbox,
 *  with glowing white eyes/markings in the dark. */
public class LimboVexRenderer extends VexRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_vex.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_vex_glow.png");

    public LimboVexRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.9F;
        this.addLayer(new LimboGlowLayer<>(this, GLOW));
    }

    @Override
    public ResourceLocation getTextureLocation(Vex entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Vex entity, PoseStack pose, float partialTick) {
        super.scale(entity, pose, partialTick); // vanilla VexRenderer applies no model scale (no-op)...
        pose.scale(2.0F, 2.0F, 2.0F);           // ...so x2 alone gives a 2x vex matching the baked 2x hitbox
    }
}
