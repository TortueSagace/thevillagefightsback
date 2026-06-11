package com.example.villagergunners;

import net.minecraft.world.entity.Entity;

/**
 * Per-entity "upgraded" flag (persistent, one-way). Defenders start nerfed and permanently upgrade to
 * the full gun loadout the first time they are near a level-3 Village Gravemind (see {@link UpgradeEvents}).
 * Stored in {@link Entity#getPersistentData()} so it survives saves; once set it is never cleared.
 */
public final class UpgradeState {
    private UpgradeState() {}

    private static final String NBT_UPGRADED = "vgun_upgraded";
    private static final String NBT_STAGE4 = "vgun_stage4"; // stage-4 defender: its attacks apply Emerald Virus II

    public static boolean isUpgraded(Entity e) {
        return e.getPersistentData().getBoolean(NBT_UPGRADED);
    }

    public static void setUpgraded(Entity e) {
        e.getPersistentData().putBoolean(NBT_UPGRADED, true);
    }

    /** Stage-4 defender: fully upgraded AND its weapon attacks apply Emerald Virus II (vs. Virus I at stage 3). */
    public static boolean isStage4(Entity e) {
        return e.getPersistentData().getBoolean(NBT_STAGE4);
    }

    public static void setStage4(Entity e) {
        e.getPersistentData().putBoolean(NBT_STAGE4, true);
        setUpgraded(e); // stage 4 implies the full stage-3 loadout
    }
}
