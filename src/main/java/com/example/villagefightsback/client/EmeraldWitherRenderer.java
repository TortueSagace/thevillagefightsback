package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * Renders the Emerald Wither: the vanilla WitherBoss renderer reskinned with the emerald wither texture and an
 * emissive layer of glowing eyes. The texture is ALWAYS the emerald wither (the invulnerable spawn phase is
 * ignored), and an anonymous {@link EyesLayer} adds the fullbright eyes overlay.
 */
public class EmeraldWitherRenderer extends WitherBossRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_wither.png");

    public EmeraldWitherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        // Fullbright eyes overlay; the anonymous EyesLayer infers <WitherBoss, model> from this renderer.
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_wither_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_wither"));
    }

    @Override
    public ResourceLocation getTextureLocation(WitherBoss entity) {
        return TEXTURE;
    }
}
