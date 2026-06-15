package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Vex;

/** Limbo Vex: the vanilla vex renderer re-skinned, scaled 3x, glowing green in the dark. */
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
        super.scale(entity, pose, partialTick); // vanilla vex 0.4 baseline...
        pose.scale(3.0F, 3.0F, 3.0F);           // ...x3 -> 3x a vanilla vex
    }
}
