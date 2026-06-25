package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * Renders the Emerald Enderman with its own texture and an emissive overlay of glowing green eyes
 * (fullbright, like vanilla enderman eyes).
 */
public class EmeraldEndermanRenderer extends EndermanRenderer {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/emerald_enderman.png");

    public EmeraldEndermanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                TheVillageFightsBack.id("textures/entity/emerald_enderman_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_enderman"));
    }

    @Override
    public ResourceLocation getTextureLocation(EnderMan entity) {
        return TEXTURE;
    }
}
