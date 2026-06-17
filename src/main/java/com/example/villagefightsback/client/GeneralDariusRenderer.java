package com.example.villagefightsback.client;

import com.example.villagefightsback.GeneralDariusEntity;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * General Darius renders as a LIVING villager guard, using {@link DariusVillagerModel} -- a humanoid model with
 * the iconic villager NOSE -- so he has a real villager face (the nose pokes out past the Limbo helmet) while
 * wearing the Limbo set (chest / legs / boots) through {@link DariusArmorLayer}, which deliberately does NOT
 * draw the helmet -- so his villager head stays bare and the face is actually visible (the helmet item is still
 * equipped for stats and to anchor the spikes). His skin (general_darius.png) is the vanilla zombie-villager
 * texture recoloured to a living villager tan, so he is NOT green/undead. He holds the Alien Smasher
 * ({@link ItemInHandLayer}) and wears the Limbo Helmet's three spikes ({@link LimboHelmetSpikesLayer}).
 */
public class GeneralDariusRenderer extends MobRenderer<GeneralDariusEntity, DariusVillagerModel> {
    private static final ResourceLocation TEX =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/general_darius.png");

    public GeneralDariusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DariusVillagerModel(ctx.bakeLayer(TheVillageFightsBackClient.DARIUS_LAYER)), 0.5F);
        addLayer(new DariusArmorLayer(this,
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
