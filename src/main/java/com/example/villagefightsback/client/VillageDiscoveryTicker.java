package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ticks down the {@link VillageDiscoveryHud} timers once per client tick, so a message always expires.
 *
 * <p>A message only counts down while the player can actually SEE it. The world-creation screens deliberately
 * report themselves as non-pausing (the integrated server has to keep running to handle the mode choice), and
 * they cover the screen with full-bleed art, so without this a message spent its whole life hidden behind them
 * and was gone by the time the player reached the world. That is exactly what happened to the starter-bell hint.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VillageDiscoveryTicker {
    private VillageDiscoveryTicker() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;        // never count down while the game is paused
        if (mc.screen != null) return;    // nor behind a screen covering the HUD: the message would go unseen
        VillageDiscoveryHud.tickDown();
    }
}
