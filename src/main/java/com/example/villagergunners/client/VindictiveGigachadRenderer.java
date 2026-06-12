package com.example.villagergunners.client;

import com.example.villagergunners.VillagerGunners;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renders a Vindictive Gigachad: a green, emerald-incrusted, vine-covered golem (texture themed like the
 * mod's "rich" blocks) carrying the rocket launcher.
 */
public class VindictiveGigachadRenderer extends IronGolemRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(VillagerGunners.MODID, "textures/entity/vindictive_gigachad.png");

    public VindictiveGigachadRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new HeldGunLayer<>(this,
                new ResourceLocation(VillagerGunners.MODID, "rocket_launcher"), "right_arm",
                -0.72F, 1.55F, -0.30F, -90.0F, 0.0F, 0.0F, 1.6F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                HeldGunLayer.Gate.HELD_GUN));
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem golem) {
        return TEXTURE; // green + emerald gems + vines, baked into the skin
    }
}
