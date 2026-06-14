package com.example.villagefightsback.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The Limbo's sky: a dark-green void (the way the End's is dark purple). SkyType.NONE means the sky is just
 * the fog colour -- a uniform dark green -- with no sun, moon, stars or clouds. Registered client-side for
 * the dimension's effects key in {@code TheVillageFightsBackClient}; the dimension_type points its {@code effects}
 * at it.
 */
public class LimboDimensionEffects extends DimensionSpecialEffects {
    public LimboDimensionEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false); // no clouds, no ground horizon, plain sky, dim
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        float b = 0.5F + brightness * 0.5F;
        return new Vec3(0.06 * b, 0.28 * b, 0.13 * b); // dark green
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null; // no sunrise/sunset tint
    }
}
