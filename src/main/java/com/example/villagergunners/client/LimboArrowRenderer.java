package com.example.villagergunners.client;

import com.example.villagergunners.LimboArrowEntity;
import com.example.villagergunners.VillagerGunners;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the Limbo Arrow using the standard arrow geometry with a black arrow texture. */
public class LimboArrowRenderer extends ArrowRenderer<LimboArrowEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(VillagerGunners.MODID, "textures/entity/limbo_arrow.png");

    public LimboArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(LimboArrowEntity entity) {
        return TEXTURE;
    }
}
