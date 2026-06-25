package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Ticks down the {@link VillageDiscoveryHud} timer once per unpaused client tick, so it always expires. */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class VillageDiscoveryTicker {
    private VillageDiscoveryTicker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return; // do not count down while the game is paused
        VillageDiscoveryHud.tickDown();
    }
}
