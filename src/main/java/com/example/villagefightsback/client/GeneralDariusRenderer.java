package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.ZombieVillager;

/**
 * General Darius renders as a LIVING villager guard, NOT a zombie. We keep the vanilla
 * {@link ZombieVillagerRenderer} -- its {@code ZombieVillagerModel} is a HumanoidModel with the iconic villager
 * nose, so the worn Limbo armour shows (via the renderer's HumanoidArmorLayer), the villager profession layer
 * still paints a normal villager's brow/clothing, and he keeps a true villager face like the village's other
 * guards. The ONLY change is the base skin: we override the texture from the green zombie-villager skin to a
 * tan, normal-villager-toned one ({@code general_darius.png}), so he reads as a man, not an undead. The
 * {@link LimboHelmetSpikesLayer} adds the Limbo Helmet's three spikes, matching a full-set wearer.
 */
public class GeneralDariusRenderer extends ZombieVillagerRenderer {
    private static final ResourceLocation TEX =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/general_darius.png");

    public GeneralDariusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        // The zombie-villager model is a HumanoidModel, so the (generic) spikes layer attaches cleanly.
        this.addLayer(new LimboHelmetSpikesLayer<ZombieVillager,
                net.minecraft.client.model.ZombieVillagerModel<ZombieVillager>>(this, ctx.getModelSet()));
    }

    /** Tan, living-villager skin instead of the green zombie one (so Darius looks like a guard, not a zombie). */
    @Override
    public ResourceLocation getTextureLocation(ZombieVillager entity) {
        return TEX;
    }
}
