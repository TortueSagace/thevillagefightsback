package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboGear;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Client half of full-set bedrock phasing: the local player's collision is predicted client-side, so the
 * server-side noPhysics ({@link LimboGearEvents}) must be matched here or the player rubber-bands when trying
 * to walk through bedrock. Both sides compute the same condition (full set + box overlapping bedrock).
 */
public final class LimboBedrockPhaseClient {
    private LimboBedrockPhaseClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isSpectator()) return;
        p.noPhysics = LimboGear.hasFullSet(p) && LimboGear.touchesBedrock(p);
    }
}
