package com.example.villagefightsback.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * A fullbright emissive overlay (like spider/enderman eyes) that makes a Limbo raider glow green in the dark.
 * Generic over any entity + model, so it drops onto the vanilla illager / ravager / vex renderers we extend.
 */
public class LimboGlowLayer<T extends Entity, M extends EntityModel<T>> extends EyesLayer<T, M> {
    private final RenderType renderType;

    public LimboGlowLayer(RenderLayerParent<T, M> parent, ResourceLocation glowTexture) {
        super(parent);
        this.renderType = RenderType.eyes(glowTexture);
    }

    @Override
    public RenderType renderType() {
        return renderType;
    }
}
