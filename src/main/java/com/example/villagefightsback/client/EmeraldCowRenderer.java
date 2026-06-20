package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldCow;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;

/**
 * Renders the Emerald Cow: a vanilla cow shape with its own emerald-faction texture and an emissive
 * (fullbright) layer of glowing green eyes, like spider eyes, so they glow in the dark.
 */
public class EmeraldCowRenderer extends CowRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_cow.png");

    public EmeraldCowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_cow_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Cow entity) {
        return TEXTURE;
    }
}
