package com.example.villagefightsback.platform.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;

/**
 * The sky, fog and cloud treatment for a dimension, in Forge's shape.
 *
 * <p>Forge keys these by an effects id that a dimension_type json points at. Fabric keys them by the dimension's
 * own id instead, so the registration is redirected to the Limbo dimension itself -- the mod ships exactly one of
 * these, and its effects id and dimension id are both "thevillagefightsback:limbo".
 */
@Environment(EnvType.CLIENT)
public final class RegisterDimensionSpecialEffectsEvent {
    public void register(ResourceLocation effectsId, DimensionSpecialEffects effects) {
        DimensionRenderingRegistry.registerDimensionEffects(effectsId, effects);
    }
}
