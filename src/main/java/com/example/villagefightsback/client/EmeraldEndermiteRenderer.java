package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldEndermite;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.model.EndermiteModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Emerald Endermite: the vanilla endermite MODEL (our mite extends Monster, not Endermite -- see
 * {@link EmeraldEndermite}) with its own emerald texture, an emissive glowing-green-eyes layer, and the shared
 * animated salt/pepper glow.
 */
public class EmeraldEndermiteRenderer extends MobRenderer<EmeraldEndermite, EndermiteModel<EmeraldEndermite>> {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/emerald_endermite.png");

    public EmeraldEndermiteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new EndermiteModel<>(ctx.bakeLayer(ModelLayers.ENDERMITE)), 0.3F);
        final RenderType eyes = RenderType.eyes(
                TheVillageFightsBack.id("textures/entity/emerald_endermite_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_endermite"));
    }

    @Override
    public ResourceLocation getTextureLocation(EmeraldEndermite entity) {
        return TEXTURE;
    }
}
