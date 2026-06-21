package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renders the Emerald Gigachad Golem: the gigachad golem reskinned for the emerald faction. Same as the
 * {@link GigachadGolemRenderer} (a gold-shell-over-iron golem holding the rocket launcher) but the shell uses
 * the emeralized gold texture. The green head spike is added centrally in {@code addEmeraldSpikes}.
 */
public class EmeraldGigachadGolemRenderer extends IronGolemRenderer {
    private static final ResourceLocation BODY =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_iron_golem.png");
    private static final ResourceLocation SHELL =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_gigachad_golem.png");

    public EmeraldGigachadGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new GoldGolemLayer(this, SHELL)); // emerald-gold shell (covers the iron body underneath)
        addLayer(new HeldWeaponLayer<>(this,
                new ResourceLocation(TheVillageFightsBack.MODID, "rocket_launcher"), "right_arm",
                -0.72F, 1.55F, -0.30F, -90.0F, 0.0F, 0.0F, 1.6F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                HeldWeaponLayer.Gate.HELD_GUN));
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return BODY;
    }
}
