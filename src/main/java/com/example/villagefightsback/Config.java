package com.example.villagefightsback;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Mod configuration (Forge COMMON config -> {@code config/gobacktoyourworld-common.toml}).
 */
public final class Config {
    private Config() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SPAWN_AT_NEAREST_VILLAGE;
    public static final ForgeConfigSpec.DoubleValue DRIFT_OVERWORLD_CHANCE;
    public static final ForgeConfigSpec.DoubleValue DRIFT_NETHER_CHANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SPAWN_AT_NEAREST_VILLAGE = b
                .comment("If true, the overworld spawn point is moved onto the nearest village when the server",
                         "starts, so players spawn in a village. Off by default -- players start at the vanilla",
                         "spawn (and begin with a Bell to found their own village).")
                .define("spawnAtNearestVillage", false);
        DRIFT_OVERWORLD_CHANCE = b
                .comment("Fraction of Limbo chunks that mirror their overworld twin (dimensional drift).")
                .defineInRange("driftOverworldChance", 0.02, 0.0, 1.0);
        DRIFT_NETHER_CHANCE = b
                .comment("Fraction of Limbo chunks that mirror their nether twin (dimensional drift).")
                .defineInRange("driftNetherChance", 0.01, 0.0, 1.0);
        SPEC = b.build();
    }

    /** Push the loaded config values into the systems that cache them. Call on config (re)load. */
    public static void apply() {
        LimboDrift.overworldChance = DRIFT_OVERWORLD_CHANCE.get();
        LimboDrift.netherChance = DRIFT_NETHER_CHANCE.get();
    }
}
