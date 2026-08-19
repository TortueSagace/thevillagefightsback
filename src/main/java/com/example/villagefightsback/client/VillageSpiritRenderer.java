package com.example.villagefightsback.client;

import net.minecraft.client.renderer.entity.AllayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The Village Spirit renders as a vanilla Allay -- it IS one, as far as the model and texture are concerned --
 * with the blue halo of {@link SpiritAuraLayer} on top.
 *
 * <p>Subclassing {@code AllayRenderer} rather than registering it directly is the whole reason this class
 * exists: a layer can only be added from inside a renderer's constructor, and the aura belongs to the spirit
 * alone. Vanilla allays are untouched.
 */
public class VillageSpiritRenderer extends AllayRenderer {
    public VillageSpiritRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new SpiritAuraLayer(this));
    }
}
