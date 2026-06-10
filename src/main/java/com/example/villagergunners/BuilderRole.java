package com.example.villagergunners;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * The "builder" role. Each village wall is built by up to {@link #BUILDER_COUNT} builders, one per gate
 * (door). A builder is assigned a door index, starts at that door, and builds the wall rightward to the next
 * door, raising each column as a full pillar. Builders are named simply "builder" (no number). A builder
 * that can't find/finish work steps DOWN (so it never wanders around still wearing the tag); the village
 * refills the freed door from a nearby villager that isn't on the step-down cool-down. When the whole wall
 * is finished the roles are dropped and they go back to being normal villagers.
 */
public final class BuilderRole {
    private BuilderRole() {}

    public static final int BUILDER_COUNT = 4;          // one builder per gate (N/S/E/W)
    private static final String NBT_BUILDER = "vgun_builder";
    private static final String NBT_DOOR = "vgun_builder_door";
    private static final String NBT_COOLDOWN = "vgun_builder_cd"; // gametime a stepped-down villager is re-eligible
    private static final long DEMOTE_COOLDOWN = 100;    // ~5s before a stepped-down villager can be re-promoted
    private static final String BUILDER_NAME = "builder";

    public static boolean isBuilder(Villager v) {
        return v.getPersistentData().getBoolean(NBT_BUILDER);
    }

    /** The gate (door) index this builder starts from and builds rightward from. */
    public static int door(Villager v) {
        return v.getPersistentData().getInt(NBT_DOOR);
    }

    public static void promote(Villager v, int door) {
        v.getPersistentData().putBoolean(NBT_BUILDER, true);
        v.getPersistentData().putInt(NBT_DOOR, door);
        v.setCustomName(Component.literal(BUILDER_NAME));
        v.setCustomNameVisible(true);
    }

    public static void demote(Villager v) {
        if (!isBuilder(v)) return;
        v.getPersistentData().putBoolean(NBT_BUILDER, false);
        v.getPersistentData().putLong(NBT_COOLDOWN, v.level().getGameTime() + DEMOTE_COOLDOWN);
        if (v.getCustomName() != null && BUILDER_NAME.equals(v.getCustomName().getString())) {
            v.setCustomName(null);
            v.setCustomNameVisible(false);
        }
    }

    /** A villager that may be promoted: not already a builder and not on the step-down cool-down.
     *  Emeraldgers can never take a role, so they are never eligible. */
    public static boolean eligible(Villager v) {
        return !(v instanceof EmeraldgerEntity)
                && !isBuilder(v) && v.level().getGameTime() >= v.getPersistentData().getLong(NBT_COOLDOWN);
    }
}
