package com.example.villagefightsback.client;

import com.example.villagefightsback.GeneralDariusEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.ModelManager;

/**
 * Renders General Darius's full Limbo set, INCLUDING the helmet, over the player model he now uses (the
 * {@link LimboHelmetSpikesLayer} spikes then sit on the actual helmet rather than floating on a bare head).
 * This is a thin subclass of {@link HumanoidArmorLayer} kept only so the renderer wiring stays explicit.
 */
public class DariusArmorLayer
        extends HumanoidArmorLayer<GeneralDariusEntity, PlayerModel<GeneralDariusEntity>, HumanoidModel<GeneralDariusEntity>> {

    public DariusArmorLayer(RenderLayerParent<GeneralDariusEntity, PlayerModel<GeneralDariusEntity>> parent,
                            HumanoidModel<GeneralDariusEntity> inner, HumanoidModel<GeneralDariusEntity> outer,
                            ModelManager models) {
        super(parent, inner, outer, models);
    }
}
