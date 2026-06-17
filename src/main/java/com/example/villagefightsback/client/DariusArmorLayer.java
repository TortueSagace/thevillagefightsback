package com.example.villagefightsback.client;

import com.example.villagefightsback.GeneralDariusEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Renders General Darius's Limbo set (chest / legs / boots) but NEVER the helmet: a full helmet armour model
 * would cover his head and hide the villager face, which is the whole point of him. The Limbo Helmet item is
 * still equipped on his head -- for its armour stats and to anchor the {@link LimboHelmetSpikesLayer} spikes --
 * it is simply not drawn over his face. Done by making every part invisible when the HEAD slot is rendered.
 */
public class DariusArmorLayer
        extends HumanoidArmorLayer<GeneralDariusEntity, DariusVillagerModel, HumanoidModel<GeneralDariusEntity>> {

    public DariusArmorLayer(RenderLayerParent<GeneralDariusEntity, DariusVillagerModel> parent,
                            HumanoidModel<GeneralDariusEntity> inner, HumanoidModel<GeneralDariusEntity> outer,
                            ModelManager models) {
        super(parent, inner, outer, models);
    }

    @Override
    protected void setPartVisibility(HumanoidModel<GeneralDariusEntity> model, EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) {
            model.setAllVisible(false); // never draw the helmet over Darius's villager face
            return;
        }
        super.setPartVisibility(model, slot);
    }
}
