package com.example.villagergunners.client;

import com.example.villagergunners.VillagerGunners;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Renders a Vindictive Gigachad: the vanilla iron golem + a held rocket launcher + a green-vine overlay. */
public class VindictiveGigachadRenderer extends IronGolemRenderer {
    public VindictiveGigachadRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new VindictiveVinesLayer(this));
        addLayer(new HeldGunLayer<>(this,
                new ResourceLocation(VillagerGunners.MODID, "rocket_launcher"), "right_arm",
                -0.72F, 1.55F, -0.30F, -90.0F, 0.0F, 0.0F, 1.6F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                HeldGunLayer.Gate.HELD_GUN));
    }
}
