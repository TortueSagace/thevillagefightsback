package com.example.villagefightsback.infection;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * The "miner" role (NBT flag, mirrors {@code BuilderRole}). At brainNode level 2, 20% of nearby villagers
 * become miners: they carry iron tools, hunt down rich (emerald-bearing) infection blocks, dig them, and
 * run the emerald back to the brain node. A miner that dies frees the slot; the village refills it.
 */
public final class MinerRole {
    private MinerRole() {}

    private static final String NBT_MINER = "vgun_miner";
    private static final String NBT_COOLDOWN = "vgun_miner_cd";
    private static final long DEMOTE_COOLDOWN = 100L;
    private static final String MINER_NAME = "miner";

    public static boolean isMiner(Villager v) {
        return v.getPersistentData().getBoolean(NBT_MINER);
    }

    public static void promote(Villager v) {
        if (isLimboSage(v)) return; // the Limbo Sage keeps its job; it never mines
        v.getPersistentData().putBoolean(NBT_MINER, true);
        v.setCustomName(Component.literal(MINER_NAME));
        v.setCustomNameVisible(true);
    }

    public static void demote(Villager v) {
        if (!isMiner(v)) return;
        v.getPersistentData().putBoolean(NBT_MINER, false);
        v.getPersistentData().putLong(NBT_COOLDOWN, v.level().getGameTime() + DEMOTE_COOLDOWN);
        if (v.getCustomName() != null && v.getCustomName().getString().startsWith(MINER_NAME)) {
            v.setCustomName(null);
            v.setCustomNameVisible(false);
        }
    }

    /** A villager that may be promoted to miner: not already one and not on the step-down cooldown.
     *  Emeraldgers can never take a role, so they are never eligible. */
    public static boolean eligible(Villager v) {
        return !(v instanceof com.example.villagefightsback.EmeraldgerEntity) && !isLimboSage(v)
                && !isMiner(v) && v.level().getGameTime() >= v.getPersistentData().getLong(NBT_COOLDOWN);
    }

    /** The Limbo Sage works at the brain node and must never be drafted as a miner. */
    static boolean isLimboSage(Villager v) {
        return v.getVillagerData().getProfession() == com.example.villagefightsback.ModVillagers.LIMBO_SAGE.get();
    }
}
