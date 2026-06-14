package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboGear;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of full-set bedrock phasing: the local player's collision is predicted client-side, so the
 * server-side noPhysics ({@link LimboGearEvents}) must be matched here or the player rubber-bands when trying
 * to walk through bedrock. Both sides compute the same condition (full set + box overlapping bedrock).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LimboBedrockPhaseClient {
    private LimboBedrockPhaseClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isSpectator()) return;
        p.noPhysics = LimboGear.hasFullSet(p) && LimboGear.touchesBedrock(p);
    }
}
