package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldPig;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Pig;

/**
 * Renders the Emerald Pig: the vanilla pig model with its OWN emerald texture and an emissive layer of shiny
 * green eyes (fullbright, like spider eyes, so they glow in the dark).
 */
public class EmeraldPigRenderer extends PigRenderer {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/emerald_pig.png");

    public EmeraldPigRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                TheVillageFightsBack.id("textures/entity/emerald_pig_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_pig"));
    }

    @Override
    public ResourceLocation getTextureLocation(Pig entity) {
        return TEXTURE;
    }
}
