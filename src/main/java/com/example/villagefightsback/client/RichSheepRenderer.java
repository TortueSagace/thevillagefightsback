package com.example.villagefightsback.client;

import com.example.villagefightsback.RichSheepEntity;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Rich Sheep: a sheep with the fertile/rich (green, emerald-flecked) skin. The body uses the
 * vanilla {@link ModelLayers#SHEEP} geometry; a custom fur layer (mirroring vanilla {@code SheepFurLayer},
 * but on {@link ModelLayers#SHEEP_FUR} with the rich fur texture and NO colour tint -- the texture is
 * already green) draws the wool on top.
 */
public class RichSheepRenderer extends MobRenderer<RichSheepEntity, SheepModel<RichSheepEntity>> {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/emerald_sheep.png");

    public RichSheepRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SheepModel<>(ctx.bakeLayer(ModelLayers.SHEEP)), 0.7F);
        this.addLayer(new RichSheepFurLayer(this, ctx.getModelSet()));
        final RenderType eyes = RenderType.eyes(
                TheVillageFightsBack.id("textures/entity/emerald_sheep_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        this.addLayer(new EmeraldGlowLayer<>(this, "emerald_sheep"));
    }

    @Override
    public ResourceLocation getTextureLocation(RichSheepEntity entity) {
        return TEXTURE;
    }

    /** Vanilla SheepFurLayer logic, but rendered at full-white RGB (no tint) with our rich fur texture. */
    private static class RichSheepFurLayer extends RenderLayer<RichSheepEntity, SheepModel<RichSheepEntity>> {
        private static final ResourceLocation FUR =
                TheVillageFightsBack.id("textures/entity/rich_sheep_fur.png");
        private final SheepFurModel<RichSheepEntity> model;

        RichSheepFurLayer(RenderLayerParent<RichSheepEntity, SheepModel<RichSheepEntity>> parent,
                          EntityModelSet modelSet) {
            super(parent);
            this.model = new SheepFurModel<>(modelSet.bakeLayer(ModelLayers.SHEEP_FUR));
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, RichSheepEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (entity.isSheared()) return;
            if (entity.isInvisible()) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.shouldEntityAppearGlowing(entity)) {
                    this.getParentModel().copyPropertiesTo(this.model);
                    this.model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
                    this.model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                    var vc = buffer.getBuffer(RenderType.outline(FUR));
                    this.model.renderToBuffer(pose, vc, light,
                            LivingEntityRenderer.getOverlayCoords(entity, 0.0F), 0xFF000000);
                }
                return;
            }
            // Full white RGB -- the rich fur texture is already green, so we do not tint it.
            coloredCutoutModelCopyLayerRender(this.getParentModel(), this.model, FUR, pose, buffer, light,
                    entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick,
                    0xFFFFFFFF);
        }
    }
}
