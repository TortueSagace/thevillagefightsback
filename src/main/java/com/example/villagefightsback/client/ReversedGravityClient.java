package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client-only half of {@link com.example.villagefightsback.ReversedGravity}: while the dimension's gravity is
 * reversed, the world is drawn upside down.
 *
 * <p>ROLL only. Roll is the last rotation applied in eye space, so it spins the finished image in the screen
 * plane and puts the sky at the bottom -- which is exactly "what is up looks down". Pitch is deliberately left
 * alone: it changes where the camera actually POINTS, which would decouple the view from the crosshair and
 * break aiming and block-breaking.
 *
 * <p>Additive, in the same style as {@link ScreenShakeClient}, so the two compose in either order and the
 * shake is never silently eaten. That class needs no changes.
 *
 * <p>The HUD is not rotated, because it is drawn in a separate orthographic pass after the world. That is
 * wanted -- an upside-down hotbar is unusable. The held item does rotate with the world.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ReversedGravityClient {
    private ReversedGravityClient() {}

    /** Ticks spent rotating in, and again rotating out. Snapping 180 degrees reads as a rendering glitch. */
    private static final float EASE = 8.0F;

    private static int ticksLeft;
    private static int totalTicks = 1;

    /** Called from the packet handler on the client. */
    public static void begin(int durationTicks) {
        ticksLeft = Math.max(ticksLeft, durationTicks);
        totalTicks = Math.max(1, Math.max(totalTicks, durationTicks));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ticksLeft > 0 && --ticksLeft == 0) totalTicks = 1;   // reset so the next flip eases from scratch
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (ticksLeft <= 0) return;
        // Fractional ticks remaining, so the ease is frame-rate smooth rather than stepping once per tick.
        float left = ticksLeft - (float) event.getPartialTick();
        float elapsed = totalTicks - left;
        float f = Math.min(1.0F, Math.min(elapsed / EASE, left / EASE));
        if (f <= 0.0F) return;
        f = f * f * (3.0F - 2.0F * f);                           // smoothstep
        event.setRoll(event.getRoll() + 180.0F * f);
    }
}
