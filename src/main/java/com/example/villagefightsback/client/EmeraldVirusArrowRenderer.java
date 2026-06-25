package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldVirusArrowEntity;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the Emerald Virus Arrow using the standard arrow geometry with a green arrow texture. */
public class EmeraldVirusArrowRenderer extends ArrowRenderer<EmeraldVirusArrowEntity> {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/emerald_virus_arrow.png");

    public EmeraldVirusArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(EmeraldVirusArrowEntity entity) {
        return TEXTURE;
    }
}
