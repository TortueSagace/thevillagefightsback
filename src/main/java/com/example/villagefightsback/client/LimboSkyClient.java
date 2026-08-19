package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboDimension;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.Minecraft;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.event.ViewportEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * Greens the Limbo's fog/horizon on the CLIENT, regardless of the dimension's baked effects. This is the
 * belt-and-braces that tints an EXISTING save's Limbo green (the dimension_type's sky effect only applies to
 * worlds created after the change). New worlds also get the full green void sky from
 * {@link LimboDimensionEffects}.
 */
public final class LimboSkyClient {
    private LimboSkyClient() {}

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && LimboDimension.LIMBO.equals(mc.player.level().dimension())) {
            event.setRed(0.06F);
            event.setGreen(0.28F);
            event.setBlue(0.13F);
        }
    }
}
