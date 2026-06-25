package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldChicken;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;

/**
 * Renders the Emerald Chicken: the vanilla chicken model with its OWN texture and an emissive layer of shiny
 * green eyes (fullbright, like spider eyes, so they glow in the dark).
 */
public class EmeraldChickenRenderer extends ChickenRenderer {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/emerald_chicken.png");

    public EmeraldChickenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                TheVillageFightsBack.id("textures/entity/emerald_chicken_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_chicken"));
    }

    @Override
    public ResourceLocation getTextureLocation(Chicken entity) {
        return TEXTURE;
    }
}
