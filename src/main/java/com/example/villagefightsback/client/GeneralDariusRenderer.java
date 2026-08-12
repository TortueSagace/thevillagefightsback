package com.example.villagefightsback.client;

import com.example.villagefightsback.GeneralDariusEntity;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * General Darius renders as a HUMAN commander on the vanilla player model.
 *
 * <p>He used to use a villager-nosed model with the zombie-villager texture recoloured tan, which made him
 * unmistakably a villager. Beside MCA Reborn's human villagers he was then the only big-nosed figure in town,
 * so the nose is gone and his skin (general_darius.png) is now a PLAYER-layout texture: the vanilla wide player
 * skin with its shirt and trousers recoloured into a general's green uniform (see tools/gen_darius_skin.py).
 *
 * <p>Only his appearance changed. He is still his own entity with his own boss bar, AI, navigation and Limbo
 * kit, so nothing about how he fights or is treated by the village is affected. He wears the Limbo set through
 * {@link DariusArmorLayer}, holds the Alien Smasher ({@link ItemInHandLayer}) and carries the Limbo Helmet's
 * three spikes ({@link LimboHelmetSpikesLayer}).
 */
public class GeneralDariusRenderer extends MobRenderer<GeneralDariusEntity, PlayerModel<GeneralDariusEntity>> {
    private static final ResourceLocation TEX =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/general_darius.png");

    public GeneralDariusRenderer(EntityRendererProvider.Context ctx) {
        // The wide (non-slim) player model, so the vanilla-derived skin lines up exactly.
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
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
