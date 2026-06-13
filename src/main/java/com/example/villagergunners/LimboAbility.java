package com.example.villagergunners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side state + gate for the full-set Limbo banish ability. Each wearer has a 30-second cooldown; the
 * remaining time is mirrored to that player's client (for the HUD) on every change. The ability only works
 * while the full Limbo armour set is worn.
 */
public final class LimboAbility {
    private LimboAbility() {}

    public static final int COOLDOWN_TICKS = 600; // 30 seconds

    private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();

    /** Per-tick upkeep for one wearer: tick the cooldown down (and keep the client in sync near zero). */
    public static void tick(ServerPlayer player) {
        Integer cd = COOLDOWN.get(player.getUUID());
        if (cd == null || cd <= 0) return;
        int next = cd - 1;
        if (next <= 0) {
            COOLDOWN.remove(player.getUUID());
            sync(player, 0); // tell the client it's ready
        } else {
            COOLDOWN.put(player.getUUID(), next);
        }
    }

    /** Fire the beam if the player wears the full set and is off cooldown. Returns true if it fired. */
    public static boolean tryFire(ServerPlayer player) {
        if (!LimboGear.hasFullSet(player)) return false;
        if (COOLDOWN.getOrDefault(player.getUUID(), 0) > 0) return false;
        LimboBeam.fire(player);
        COOLDOWN.put(player.getUUID(), COOLDOWN_TICKS);
        sync(player, COOLDOWN_TICKS);
        return true;
    }

    public static int getCooldown(UUID id) {
        return COOLDOWN.getOrDefault(id, 0);
    }

    private static void sync(ServerPlayer player, int ticks) {
        com.example.villagergunners.network.ModNetwork.sendTo(player,
                new com.example.villagergunners.network.LimboCooldownPacket(ticks));
    }

    /** When a wearer first completes the set, push the current cooldown so the HUD is correct immediately. */
    public static void syncOnEquip(ServerPlayer player) {
        sync(player, getCooldown(player.getUUID()));
    }
}
