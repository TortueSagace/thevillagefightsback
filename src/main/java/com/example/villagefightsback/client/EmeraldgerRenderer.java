package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldgerEntity;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Emeraldger: a villager-shaped mob with its OWN texture (white skin, yellow clothes) instead of
 * the vanilla biome/profession-composited villager skin, scaled to its HP-derived size, holding its Alien
 * Slasher (crossed-arms layer), and wearing an emissive layer of shiny green eyes (fullbright, like spider
 * eyes, so they glow in the dark).
 */
public class EmeraldgerRenderer extends MobRenderer<EmeraldgerEntity, VillagerModel<EmeraldgerEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emeraldger.png");

    public EmeraldgerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.addLayer(new CrossedArmsItemLayer<>(this, ctx.getItemInHandRenderer())); // show the held Alien Slasher
        this.addLayer(new GreenEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(EmeraldgerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(EmeraldgerEntity entity, PoseStack pose, float partialTick) {
        float s = entity.getEmeraldgerScale();
        pose.scale(s, s, s);
    }

    /** Fullbright green-eyes overlay mapped onto the villager head UV. */
    private static class GreenEyesLayer extends EyesLayer<EmeraldgerEntity, VillagerModel<EmeraldgerEntity>> {
        private static final RenderType EYES = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emeraldger_eyes.png"));

        GreenEyesLayer(RenderLayerParent<EmeraldgerEntity, VillagerModel<EmeraldgerEntity>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
