package com.example.villagefightsback.mixin.client;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.ViewportEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;

/** Forge's ViewportEvent.ComputeFogColor, which is what gives the Limbo its dark-green haze. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void tvfb$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                      float darkenWorldAmount, CallbackInfo ci) {
        if (!EventBus.hasListeners(ViewportEvent.ComputeFogColor.class)) return;
        ViewportEvent.ComputeFogColor event = EventBus.post(
                new ViewportEvent.ComputeFogColor(partialTick, fogRed, fogGreen, fogBlue));
        fogRed = event.getRed();
        fogGreen = event.getGreen();
        fogBlue = event.getBlue();
    }
}
