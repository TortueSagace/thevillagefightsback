package com.example.villagergunners;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * The "builder" role. Each village wall is built by {@link #BUILDER_COUNT} builders, one per wall layer
 * (course). A builder is visibly named "builder &lt;course&gt;" while it works and only builds its own
 * course. Builders that die free their course; the village refills it from a nearby free villager or by
 * spawning one (see GravemindEvents.manageBuilders). There is no idle-timeout demotion: in a layer conga the
 * upper-course builders intentionally wait for the layers below to get ahead.
 */
public final class BuilderRole {
    private BuilderRole() {}

    public static final int BUILDER_COUNT = 6;          // one builder per course 0..5
    private static final String NBT_BUILDER = "vgun_builder";
    private static final String NBT_COURSE = "vgun_builder_course";
    private static final String BUILDER_NAME = "builder";

    public static boolean isBuilder(Villager v) {
        return v.getPersistentData().getBoolean(NBT_BUILDER);
    }

    /** The wall layer (course) this builder is responsible for. */
    public static int course(Villager v) {
        return v.getPersistentData().getInt(NBT_COURSE);
    }

    public static void promote(Villager v, int course) {
        v.getPersistentData().putBoolean(NBT_BUILDER, true);
        v.getPersistentData().putInt(NBT_COURSE, course);
        v.setCustomName(Component.literal(BUILDER_NAME + " " + course));
        v.setCustomNameVisible(true);
    }

    public static void demote(Villager v) {
        if (!isBuilder(v)) return;
        v.getPersistentData().putBoolean(NBT_BUILDER, false);
        if (v.getCustomName() != null && v.getCustomName().getString().startsWith(BUILDER_NAME)) {
            v.setCustomName(null);
            v.setCustomNameVisible(false);
        }
    }

    /** A villager that may be promoted: not already a builder. */
    public static boolean eligible(Villager v) {
        return !isBuilder(v);
    }
}
