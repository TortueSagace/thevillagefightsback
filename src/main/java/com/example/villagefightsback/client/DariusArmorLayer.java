package com.example.villagefightsback.client;

import com.example.villagefightsback.GeneralDariusEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.ModelManager;

/**
 * Renders General Darius's full Limbo set, INCLUDING the helmet. The helmet covers his head box, but his
 * villager NOSE protrudes past the head-sized helmet, so he still reads as a villager guard (and the
 * {@link LimboHelmetSpikesLayer} spikes sit on top of the actual helmet rather than floating on a bare head).
 * This is a thin subclass of {@link HumanoidArmorLayer} kept only so the renderer wiring stays explicit.
 */
public class DariusArmorLayer
        extends HumanoidArmorLayer<GeneralDariusEntity, DariusVillagerModel, HumanoidModel<GeneralDariusEntity>> {

    public DariusArmorLayer(RenderLayerParent<GeneralDariusEntity, DariusVillagerModel> parent,
                            HumanoidModel<GeneralDariusEntity> inner, HumanoidModel<GeneralDariusEntity> outer,
                            ModelManager models) {
        super(parent, inner, outer, models);
    }
}
