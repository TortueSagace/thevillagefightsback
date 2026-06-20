package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Phantom;

/**
 * Renders the Emerald Phantom: a reskinned vanilla Phantom with its own texture and an emissive (fullbright)
 * green-eyes overlay so it glows in the dark.
 */
public class EmeraldPhantomRenderer extends PhantomRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_phantom.png");

    public EmeraldPhantomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_phantom_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Phantom entity) {
        return TEXTURE;
    }
}
