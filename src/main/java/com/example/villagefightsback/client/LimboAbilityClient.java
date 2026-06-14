package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboGear;
import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.network.LimboBeamPacket;
import com.example.villagefightsback.network.ModNetwork;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of the Limbo banish ability: on the keybind, asks the server to fire (the server gates it); and
 * keeps a local copy of the cooldown (server-synced on each use) for the HUD to draw down smoothly.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LimboAbilityClient {
    private LimboAbilityClient() {}

    private static int cooldownTicks = 0;

    /** Server -> client sync of the authoritative cooldown. */
    public static void setCooldown(int ticks) {
        cooldownTicks = Math.max(0, ticks);
    }

    public static int getCooldownTicks() {
        return cooldownTicks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && cooldownTicks > 0) cooldownTicks--;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (LimboAbilityKeybinds.LIMBO_BEAM.consumeClick()) {
            if (cooldownTicks > 0 || !LimboGear.hasFullSet(mc.player)) continue; // gated client-side too (no spam)
            ModNetwork.CHANNEL.sendToServer(new LimboBeamPacket());
        }
    }
}
