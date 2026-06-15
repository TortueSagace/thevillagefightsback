package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Evoker;

/** Limbo Evoker: the vanilla evoker renderer (keeps the spell-cast pose) re-skinned, scaled 3x, glowing. */
public class LimboEvokerRenderer extends EvokerRenderer<Evoker> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_evoker.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_evoker_glow.png");

    public LimboEvokerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4F;
        this.addLayer(new LimboGlowLayer<>(this, GLOW));
    }

    @Override
    public ResourceLocation getTextureLocation(Evoker entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Evoker entity, PoseStack pose, float partialTick) {
        super.scale(entity, pose, partialTick);
        pose.scale(3.0F, 3.0F, 3.0F);
    }
}
