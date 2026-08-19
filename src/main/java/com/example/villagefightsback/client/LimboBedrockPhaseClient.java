package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboGear;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * Client half of full-set bedrock phasing: the local player's collision is predicted client-side, so the
 * server-side noPhysics ({@link LimboGearEvents}) must be matched here or the player rubber-bands when trying
 * to walk through bedrock. Both sides compute the same condition (full set + box overlapping bedrock).
 */
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
