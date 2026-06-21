package com.example.villagefightsback;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants the mod's code-driven advancements -- the ones whose datapack criterion is {@code minecraft:impossible}
 * (so they can only be awarded from code). Each such advancement uses a single criterion named {@code got_it}.
 */
public final class Achievements {
    private Achievements() {}

    /** Award one advancement to a player (no-op if it does not exist or is already earned -- advancements dedupe). */
    public static void award(ServerPlayer player, String id) {
        if (player == null) return;
        Advancement adv = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(TheVillageFightsBack.MODID, id));
        if (adv != null) player.getAdvancements().award(adv, "got_it");
    }

    /** Award every player within {@code radius} of (x,y,z) in {@code level} -- i.e. everyone who "witnessed" it. */
    public static void awardNearby(ServerLevel level, double x, double y, double z, double radius, String id) {
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) <= r2) award(p, id);
        }
    }
}
