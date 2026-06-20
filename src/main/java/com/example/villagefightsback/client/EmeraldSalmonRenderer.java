package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldSalmon;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SalmonRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Salmon;

/**
 * Renders the Emerald Salmon: the vanilla salmon model with its OWN texture and an emissive (fullbright)
 * layer of shiny green eyes, marking it as a member of the EMERALD faction. The eyes layer is added as an
 * anonymous {@link EyesLayer}, whose generics are inferred from this renderer's {@code RenderLayerParent}.
 */
public class EmeraldSalmonRenderer extends SalmonRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_salmon.png");

    public EmeraldSalmonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_salmon_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Salmon entity) {
        return TEXTURE;
    }
}
