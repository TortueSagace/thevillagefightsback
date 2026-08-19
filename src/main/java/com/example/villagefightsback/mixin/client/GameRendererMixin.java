package com.example.villagefightsback.mixin.client;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.ViewportEvent;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import com.mojang.math.Axis;

/**
 * Forge's ViewportEvent.ComputeCameraAngles, which is how the mod rolls the world for reversed gravity.
 *
 * <p>Forge fires it right after the camera is set up and folds the angles back in. Vanilla has no roll at all, so
 * the roll a handler asks for is applied to the projection stack instead -- bobHurt is the hook because it is
 * handed that very stack, on every frame, before the world is drawn.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void tvfb$cameraAngles(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (!EventBus.hasListeners(ViewportEvent.ComputeCameraAngles.class)) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        ViewportEvent.ComputeCameraAngles event = EventBus.post(new ViewportEvent.ComputeCameraAngles(
                partialTick, camera.getYRot(), camera.getXRot(), 0.0F));
        if (event.getRoll() != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(event.getRoll()));
        }
    }
}
