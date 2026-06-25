package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Renders a Gigachad Golem and its Vindictive subclass: a gold-skinned iron golem holding the rocket launcher. */
public class GigachadGolemRenderer extends IronGolemRenderer {
    public GigachadGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new GoldGolemLayer(this));     // metallic-gold re-skin
        addLayer(new HeldWeaponLayer<>(this,
                TheVillageFightsBack.id("rocket_launcher"), "right_arm",
                -0.72F, 1.55F, -0.30F, -90.0F, 0.0F, 0.0F, 1.6F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                HeldWeaponLayer.Gate.HELD_GUN));
    }
}
