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

    public static boolean isUpgraded(Entity e) {
        return e.getPersistentData().getBoolean(NBT_UPGRADED);
    }

    public static void setUpgraded(Entity e) {
        e.getPersistentData().putBoolean(NBT_UPGRADED, true);
    }
}
