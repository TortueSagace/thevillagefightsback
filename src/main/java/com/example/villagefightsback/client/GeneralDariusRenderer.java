package com.example.villagefightsback.client;

import com.example.villagefightsback.GeneralDariusEntity;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * General Darius renders as a LIVING armoured knight -- a plain humanoid, NOT a zombie villager. The standard
 * humanoid model wears his full Limbo set (HumanoidArmorLayer), holds the Alien Smasher (ItemInHandLayer), and
 * shows the Limbo Helmet's three spikes ({@link LimboHelmetSpikesLayer}). His skin (general_darius.png) is a
 * normal tan humanoid; the worn armour covers most of it, so he reads as an armoured man.
 */
public class GeneralDariusRenderer extends MobRenderer<GeneralDariusEntity, HumanoidModel<GeneralDariusEntity>> {
    private static final ResourceLocation TEX =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/general_darius.png");

    public GeneralDariusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        addLayer(new LimboHelmetSpikesLayer<>(this, ctx.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(GeneralDariusEntity entity) {
        return TEX;
    }
}
