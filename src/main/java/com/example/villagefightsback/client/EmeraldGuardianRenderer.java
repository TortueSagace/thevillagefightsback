package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Guardian;

/**
 * Renders the Emerald Guardian: a vanilla Guardian with its OWN emerald texture and an emissive (fullbright)
 * eyes overlay so the eyes glow in the dark.
 */
public class EmeraldGuardianRenderer extends GuardianRenderer {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/emerald_guardian.png");

    public EmeraldGuardianRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(
                TheVillageFightsBack.id("textures/entity/emerald_guardian_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_guardian"));
    }

    @Override
    public ResourceLocation getTextureLocation(Guardian entity) {
        return TEXTURE;
    }
}
