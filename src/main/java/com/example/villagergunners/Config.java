package com.example.villagergunners;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Mod configuration (Forge COMMON config -> {@code config/gobacktoyourworld-common.toml}).
 */
public final class Config {
    private Config() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SPAWN_AT_NEAREST_VILLAGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SPAWN_AT_NEAREST_VILLAGE = b
                .comment("If true, the overworld spawn point is moved onto the nearest village when the server",
                         "starts, so players spawn in a village. Off by default -- players start at the vanilla",
                         "spawn (and begin with a Bell to found their own village).")
                .define("spawnAtNearestVillage", false);
        SPEC = b.build();
    }
}
