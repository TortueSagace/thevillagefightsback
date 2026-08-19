package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.event.ViewportEvent;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * Client-only camera shake. The boss sends {@link com.example.villagefightsback.network.ScreenShakePacket}s;
 * {@link #add} starts/refreshes a decaying jitter that is applied to the camera angles each frame and counts
 * down each client tick. Pure cosmetic; no gameplay effect.
 */
public final class ScreenShakeClient {
    private ScreenShakeClient() {}

    private static float intensity;   // degrees of peak jitter
    private static int ticksLeft;
    private static int maxTicks = 1;
    // a tiny deterministic PRNG so we don't allocate a Random every frame
    private static long seed = 0x9E3779B97F4A7C15L;

    public static void add(float i, int durationTicks) {
        intensity = Math.max(intensity, i);            // strongest active shake wins
        ticksLeft = Math.max(ticksLeft, durationTicks);
        maxTicks = Math.max(maxTicks, ticksLeft);
    }

    private static float nextSigned() {
        seed ^= seed << 13; seed ^= seed >>> 7; seed ^= seed << 17;
        return ((seed >>> 40) / (float) (1 << 24)) * 2.0F - 1.0F; // ~[-1,1]
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ticksLeft > 0) ticksLeft--;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ticksLeft <= 0) return;
        float decay = ticksLeft / (float) maxTicks;     // fade out over the duration
        float a = intensity * decay;
        event.setRoll(event.getRoll() + nextSigned() * a * 1.4F);
        event.setPitch(event.getPitch() + nextSigned() * a);
        event.setYaw(event.getYaw() + nextSigned() * a);
    }
}
