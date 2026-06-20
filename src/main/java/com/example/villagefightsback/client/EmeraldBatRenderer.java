package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldBat;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ambient.Bat;

/**
 * Renders the Emerald Bat: the vanilla bat model with its OWN emerald-faction texture, plus an emissive
 * (fullbright) layer of shiny green eyes so they glow in the dark, mirroring the Emeraldger's eyes overlay.
 */
public class EmeraldBatRenderer extends BatRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_bat.png");

    public EmeraldBatRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_bat_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_bat"));
    }

    @Override
    public ResourceLocation getTextureLocation(Bat entity) {
        return TEXTURE;
    }
}
