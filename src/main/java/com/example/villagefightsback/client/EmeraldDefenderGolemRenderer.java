package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Renders the Emerald Defender Golem: identical to the {@link DefenderGolemRenderer} (an iron golem body with
 * the iron head hidden and a Wall Defender drawn in its place) EXCEPT the BODY uses the emeralized iron-golem
 * texture plus the shared emerald speckle glow. The head stays a Wall Defender (the head layer is inherited)
 * and it gets NO head spike.
 */
public class EmeraldDefenderGolemRenderer extends DefenderGolemRenderer {
    private static final ResourceLocation BODY =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_iron_golem.png");

    public EmeraldDefenderGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx); // adds the wall-defender head layer + hides the iron head
        addLayer(new EmeraldGlowLayer<>(this, "emerald_iron_golem")); // emissive body speckle glow
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return BODY;
    }
}
