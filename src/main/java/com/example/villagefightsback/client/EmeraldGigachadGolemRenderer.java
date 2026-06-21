package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renders the Emerald Gigachad Golem: the iron golem model wearing the emeralized gold skin AS ITS BASE texture
 * (no separate over-scaled gold shell -- it's always an emerald gigachad, so the gold IS the body), plus the
 * shared emerald glowing-eyes + animated salt/pepper glow layers and the held rocket launcher. The green head
 * spike is added centrally in {@code addEmeraldSpikes}. Eyes/glow reuse the iron-golem-UV assets (same model).
 */
public class EmeraldGigachadGolemRenderer extends IronGolemRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_gigachad_golem.png");
    private static final ResourceLocation EYES =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_iron_golem_eyes.png");

    public EmeraldGigachadGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        final RenderType eyes = RenderType.eyes(EYES);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
        addLayer(new EmeraldGlowLayer<>(this, "emerald_iron_golem")); // animated salt/pepper glow (shared frames)
        addLayer(new HeldWeaponLayer<>(this,
                new ResourceLocation(TheVillageFightsBack.MODID, "rocket_launcher"), "right_arm",
                -0.72F, 1.55F, -0.30F, -90.0F, 0.0F, 0.0F, 1.6F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                HeldWeaponLayer.Gate.HELD_GUN));
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return TEXTURE;
    }
}
