package com.example.villagefightsback;

import net.minecraft.world.entity.Entity;

/**
 * Per-entity "upgraded" flag (persistent, one-way). Defenders start nerfed and permanently upgrade to
 * the full gun loadout the first time they are near a level-3 Village Brain Node (see {@link UpgradeEvents}).
 * Stored in {@link Entity#getPersistentData()} so it survives saves; once set it is never cleared.
 *
 * <p>The setters also mirror the flags to scoreboard tags via
 * {@link com.example.villagefightsback.compat.VillagerRoleTags} -- a no-op unless MCA Reborn is installed,
 * whose villager replacement carries tags across but not persistent data.
 */
public final class UpgradeState {
    private UpgradeState() {}

    private static final String NBT_UPGRADED = "vgun_upgraded";
    private static final String NBT_STAGE2 = "vgun_stage2";  // stage-2 guard: virus crossbow + emerald armour, no mace
    private static final String NBT_STAGE4 = "vgun_stage4"; // stage-4 defender: its attacks apply Emerald Virus II

    public static boolean isUpgraded(Entity e) {
        return e.getPersistentData().getBoolean(NBT_UPGRADED);
    }

    public static void setUpgraded(Entity e) {
        e.getPersistentData().putBoolean(NBT_UPGRADED, true);
        e.getPersistentData().putBoolean(NBT_STAGE2, true); // the full kit (stage 3+) includes the stage-2 base
        com.example.villagefightsback.compat.VillagerRoleTags.mirror(e);
    }

    /** Stage-2 guard: fires emerald-virus crossbow arrows + wears emerald armour, but has NO mace (the
     *  StageThreeGuardGoal's mace-bounce is gated to stage 3+). A virus archer, distinct from the full knight. */
    public static boolean isStage2(Entity e) {
        return e.getPersistentData().getBoolean(NBT_STAGE2);
    }

    public static void setStage2(Entity e) {
        e.getPersistentData().putBoolean(NBT_STAGE2, true);
        com.example.villagefightsback.compat.VillagerRoleTags.mirror(e);
    }

    /** Stage-4 defender: fully upgraded AND its weapon attacks apply Emerald Virus II (vs. Virus I at stage 3). */
    public static boolean isStage4(Entity e) {
        return e.getPersistentData().getBoolean(NBT_STAGE4);
    }

    public static void setStage4(Entity e) {
        e.getPersistentData().putBoolean(NBT_STAGE4, true);
        setUpgraded(e); // stage 4 implies the full stage-3 loadout (and mirrors the flags)
    }
}
