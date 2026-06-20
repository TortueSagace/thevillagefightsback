package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldCreeper;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Renders the Emerald Creeper: the vanilla creeper model with its OWN emerald-tinted texture and an emissive
 * overlay of shiny green eyes (fullbright, like spider eyes, so they glow in the dark).
 */
public class EmeraldCreeperRenderer extends CreeperRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_creeper.png");

    public EmeraldCreeperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_creeper_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Creeper entity) {
        return TEXTURE;
    }
}
