package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Pillager;

/** Limbo Pillager: the vanilla pillager renderer (keeps the crossbow layer) re-skinned, scaled 3x, glowing. */
public class LimboPillagerRenderer extends PillagerRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_pillager.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_pillager_glow.png");

    public LimboPillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4F;
        this.addLayer(new LimboGlowLayer<>(this, GLOW));
    }

    @Override
    public ResourceLocation getTextureLocation(Pillager entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Pillager entity, PoseStack pose, float partialTick) {
        super.scale(entity, pose, partialTick); // keep the illager's 0.9375 baseline...
        pose.scale(3.0F, 3.0F, 3.0F);           // ...then 3x on top, so model matches the 3x hitbox
    }
}
