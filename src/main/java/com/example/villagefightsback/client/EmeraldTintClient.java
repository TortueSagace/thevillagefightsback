package com.example.villagefightsback.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side green-tint for Emerald-Virus hosts. Progress (0..1) per entity id arrives via
 * {@link com.example.villagefightsback.network.EmeraldTintPacket}; the host is rendered progressively greener
 * by multiplying the entity shader colour. The tint never reverts (the server keeps progress forever), so a
 * fully-infected creature stays green even after the timed effect ends.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EmeraldTintClient {
    private EmeraldTintClient() {}

    /** entityId -> tint progress 0..1. */
    private static final Map<Integer, Float> TINT = new ConcurrentHashMap<>();

    /** Called from the network handler (main thread) when a tint update arrives. */
    public static void put(int entityId, float progress) {
        if (progress <= 0.0F) TINT.remove(entityId);
        else TINT.put(entityId, Math.min(1.0F, progress));
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        Float p = TINT.get(event.getEntity().getId());
        if (p == null) return;
        // Lerp white (1,1,1) -> alien green (0.25, 1.0, 0.25): drop the red/blue channels as the virus matures.
        float t = p;
        float r = 1.0F - 0.75F * t;
        float g = 1.0F;
        float b = 1.0F - 0.75F * t;
        RenderSystem.setShaderColor(r, g, b, 1.0F);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (TINT.containsKey(event.getEntity().getId())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) TINT.remove(event.getEntity().getId());
    }
}
