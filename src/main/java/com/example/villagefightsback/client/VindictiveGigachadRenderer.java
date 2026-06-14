package com.example.villagefightsback.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** A Vindictive Gigachad: the gold gigachad skin (from {@link GigachadGolemRenderer}) plus a green-vine overlay. */
public class VindictiveGigachadRenderer extends GigachadGolemRenderer {
    public VindictiveGigachadRenderer(EntityRendererProvider.Context ctx) {
        super(ctx); // gold re-skin + held rocket launcher
        addLayer(new VindictiveVinesLayer(this));
    }
}
