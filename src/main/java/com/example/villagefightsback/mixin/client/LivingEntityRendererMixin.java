package com.example.villagefightsback.mixin.client;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.RenderLivingEvent;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Forge's RenderLivingEvent, either side of one entity being drawn. The mod tints an infected mob in Pre and puts
 * the shader colour back in Post, so both have to fire and Post must fire even when Pre changed nothing.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void tvfb$renderPre(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                MultiBufferSource buffers, int light, CallbackInfo ci) {
        EventBus.post(new RenderLivingEvent.Pre<>(entity));
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void tvfb$renderPost(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, CallbackInfo ci) {
        EventBus.post(new RenderLivingEvent.Post<>(entity));
    }
}
