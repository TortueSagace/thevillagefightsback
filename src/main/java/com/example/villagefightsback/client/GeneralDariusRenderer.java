package com.example.villagefightsback.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.world.entity.monster.ZombieVillager;

/**
 * General Darius renders as a vanilla zombie villager (like the {@link com.example.villagefightsback.VindictiveGuardEntity})
 * so the worn Limbo armour shows via the vanilla HumanoidArmorLayer. We extend the vanilla
 * {@link ZombieVillagerRenderer} only to bolt on the {@link LimboHelmetSpikesLayer} so the Limbo Helmet's
 * three spikes appear on Darius's head, matching a full-set player wearer.
 */
public class GeneralDariusRenderer extends ZombieVillagerRenderer {
    public GeneralDariusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        // The zombie-villager model is a HumanoidModel, so the (generic) spikes layer attaches cleanly.
        this.addLayer(new LimboHelmetSpikesLayer<ZombieVillager,
                net.minecraft.client.model.ZombieVillagerModel<ZombieVillager>>(this, ctx.getModelSet()));
    }
}
