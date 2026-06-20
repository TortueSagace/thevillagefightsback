package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldHorse;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Horse;

/**
 * Renders the Emerald Horse: a vanilla horse model with the mod's own texture and an emissive
 * (fullbright) layer of shiny green eyes that glow in the dark. (Vanilla HorseRenderer is final, so we extend
 * AbstractHorseRenderer and bake the horse model ourselves, exactly as HorseRenderer does.)
 */
public class EmeraldHorseRenderer extends AbstractHorseRenderer<Horse, HorseModel<Horse>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_horse.png");

    public EmeraldHorseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HorseModel<>(ctx.bakeLayer(ModelLayers.HORSE)), 1.1F);
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_horse_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Horse entity) {
        return TEXTURE;
    }
}
