package com.example.villagefightsback;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The player-tunable configuration for The Village Fights Back: a single commented file
 * ({@code config/thevillagefightsback-common.toml}) that calibrates the village's dynamics, and which can also be
 * read/changed live in-game through the {@code /thevillagefightsback config <key> [value]} commands
 * ({@link ModCommands}).
 *
 * <p>Every entry is registered with a comment (which becomes a comment line in the generated TOML), a default, and
 * -- for the numeric ones -- a valid range. All MULTIPLIERS are floats defaulting to {@value #MULT_DEFAULT} and are
 * lower-bounded at {@value #MULT_MIN} (so they can never be zero/negative and never divide-by-zero). Booleans
 * default to {@code true} except the seven "off by default" switches called out in their comments.
 *
 * <p>{@link #INDEX} maps each snake_case key to its value so the commands can list/get/set generically.
 */
public final class Config {
    private Config() {}

    /** Shared bounds for every multiplier. */
    public static final double MULT_MIN = 0.1;       // never 0/negative -> no divide-by-zero, no nonsense
    public static final double MULT_MAX = 1000.0;
    public static final double MULT_DEFAULT = 1.0;

    public static final ForgeConfigSpec SPEC;

    // --- pre-existing (internal: not exposed as a tunable command) ---
    public static final ForgeConfigSpec.DoubleValue DRIFT_OVERWORLD_CHANCE;
    public static final ForgeConfigSpec.DoubleValue DRIFT_NETHER_CHANCE;

    // --- explosions: creature rockets ---
    public static final ForgeConfigSpec.BooleanValue CREATURE_ROCKET_EXPLOSION_DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue CREATURE_ROCKET_SET_BLOCKS_ON_FIRE;
    // --- explosions: air strikes ---
    public static final ForgeConfigSpec.BooleanValue AIR_STRIKE_ROCKET_EXPLOSION_DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue AIR_STRIKE_ROCKET_SET_BLOCKS_ON_FIRE;
    // --- explosions: emerald virus ---
    public static final ForgeConfigSpec.BooleanValue EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE;
    // --- feature switches ---
    public static final ForgeConfigSpec.BooleanValue BRAIN_NODE_EXTINGUISHES_FIRE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_IS_HOSTILE;
    public static final ForgeConfigSpec.BooleanValue EMERALDGERS_SIZE_VARIATION;
    public static final ForgeConfigSpec.BooleanValue MEGA_EXPANSION_MODE;
    public static final ForgeConfigSpec.BooleanValue GENERAL_DARIUS_REMAINS;
    public static final ForgeConfigSpec.BooleanValue MEGA_BRUTAL_MODE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_SPAWN_IN_VILLAGE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_IS_GIVEN_BELL_AT_SPAWN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_INFECTION;
    // --- multipliers (float, default 1.0, lower-bounded) ---
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_UNIT_SPAWN_RATE;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_NUMBER_OF_SETTLERS;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_BRAIN_INITIATIVES_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_AIR_STRIKES_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_BLOCK_INFECTION_SPEED;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_BRAIN_INFLUENCE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_BLOCK_INFECTION_REACH;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_LIMBO_RAIDERS;
    // --- integers ---
    public static final ForgeConfigSpec.IntValue AIR_STRIKES_APPEAR_AT_STAGE;
    public static final ForgeConfigSpec.IntValue UNSTABLE_LIMBO_GRAVEL_HP_THRESHOLD;
    public static final ForgeConfigSpec.IntValue GUARDS_SPAWNING_WITH_GENERAL_DARIUS;

    /** snake_case key -> value, in declaration order, for the /thevillagefightsback config commands. */
    public static final Map<String, ForgeConfigSpec.ConfigValue<?>> INDEX = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("limbo");
        DRIFT_OVERWORLD_CHANCE = b
                .comment("Fraction of Limbo chunks that mirror their overworld twin (dimensional drift).")
                .defineInRange("driftOverworldChance", 0.02, 0.0, 1.0);
        DRIFT_NETHER_CHANCE = b
                .comment("Fraction of Limbo chunks that mirror their nether twin (dimensional drift).")
                .defineInRange("driftNetherChance", 0.01, 0.0, 1.0);
        b.pop();

        b.push("explosions");
        CREATURE_ROCKET_EXPLOSION_DESTROY_BLOCKS = b
                .comment("If true, a creature/unit rocket's explosion breaks blocks. The blast still HURTS units either way.",
                        "Default: false (protects the build).")
                .define("creature_rocket_explosion_destroy_blocks", false);
        CREATURE_ROCKET_SET_BLOCKS_ON_FIRE = b
                .comment("If true, a creature/unit rocket's explosion can set blocks on fire. The blast still IGNITES units either way.")
                .define("creature_rocket_set_blocks_on_fire", true);
        AIR_STRIKE_ROCKET_EXPLOSION_DESTROY_BLOCKS = b
                .comment("If true, an air-strike rocket's explosion breaks blocks. Air strikes usually land outside the village",
                        "perimeter, so the risk to the village itself is low. The blast still hurts units either way.")
                .define("air_strike_rocket_explosion_destroy_blocks", true);
        AIR_STRIKE_ROCKET_SET_BLOCKS_ON_FIRE = b
                .comment("If true, an air-strike rocket's explosion can set blocks on fire.")
                .define("air_strike_rocket_set_blocks_on_fire", true);
        EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS = b
                .comment("If true, the explosion of a creature that dies carrying the emerald virus breaks blocks.",
                        "The blast still hurts units either way. Default: false.")
                .define("emerald_virus_explosion_destroy_blocks", false);
        EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE = b
                .comment("If true, the emerald-virus death explosion can set blocks on fire.")
                .define("emerald_virus_explosion_set_blocks_on_fire", true);
        b.pop();

        b.push("features");
        BRAIN_NODE_EXTINGUISHES_FIRE = b
                .comment("If true, a brain node will (for 1 emerald, on a 1-minute cooldown, at any stage) clear ALL vanilla",
                        "fire (normal + soul fire) within its influence radius. This mod adds no fire of its own; other",
                        "mods' custom fire is left untouched.")
                .define("brain_node_extinguishes_fire", true);
        PLAYER_IS_HOSTILE = b
                .comment("If true, every player permanently has the 'Enemy of the Village' status and can NEVER become a",
                        "Hero of the Village, no matter what. Default: false.")
                .define("player_is_hostile", false);
        EMERALDGERS_SIZE_VARIATION = b
                .comment("If true, emeraldgers spawn with a size proportional to the HP of the victim they came from (current",
                        "behaviour). If false, they all spawn at the default villager size.")
                .define("emeraldgers_size_variation", true);
        MEGA_EXPANSION_MODE = b
                .comment("If true, every minute the brain node spawns a settler that places a random colored TNT block. The",
                        "usual settler placement rules (far enough from existing buildings, etc.) still apply. Default: false.")
                .define("mega_expansion_mode", false);
        GENERAL_DARIUS_REMAINS = b
                .comment("If true, General Darius never returns to the brain node, even after every 100+ HP enemy is cleared.")
                .define("general_darius_remains", true);
        MEGA_BRUTAL_MODE = b
                .comment("If true: all village units permanently gain Strength III, Regeneration III, Resistance III and Speed III",
                        "(no potion particles); guards wield Alien Smashers in place of the regular Mace; and golems can teleport",
                        "to enemies the way the Vindictive Village Spirit does. Default: false.")
                .define("mega_brutal_mode", false);
        PLAYER_SPAWN_IN_VILLAGE = b
                .comment("If true, a newly created world moves the spawn point onto the nearest village so the player spawns",
                        "in a village. Default: false (players start at the vanilla spawn).")
                .define("player_spawn_in_village", false);
        PLAYER_IS_GIVEN_BELL_AT_SPAWN = b
                .comment("If true, the player is given a Bell when they first spawn into a new world (to found their own village).")
                .define("player_is_given_bell_at_spawn", true);
        ENABLE_BLOCK_INFECTION = b
                .comment("Master switch for block infection. If false, neither paths proliferate nor fertile blocks appear at all",
                        "(the multiplier_block_infection_* settings are then irrelevant). Default: true.")
                .define("enable_block_infection", true);
        b.pop();

        b.push("multipliers");
        MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD = mult(b,
                "multiplier_level_up_emerald_threshold",
                "Scales the emerald cost for the brain node to reach each stage. 2.0 = every stage costs twice as much.");
        MULTIPLIER_UNIT_SPAWN_RATE = mult(b,
                "multiplier_unit_spawn_rate",
                "Scales how OFTEN nodes spawn their unit. 2.0 = each unit that would spawn once a day at sunset instead spawns",
                "twice a day, at even intervals. Applies to the brain node and every node (iron golem factory, barracks, lure",
                "tower). Rounded to a whole number of spawns per day.");
        MULTIPLIER_NUMBER_OF_SETTLERS = mult(b,
                "multiplier_number_of_settlers",
                "Scales how many settlers spawn each time settlers would spawn. 2.0 = twice as many (still spaced apart, not",
                "stacked on the same spot).");
        MULTIPLIER_BRAIN_INITIATIVES_COOLDOWN = mult(b,
                "multiplier_brain_initiatives_cooldown",
                "Scales the cooldown between brain initiatives. 2.0 = brain initiatives happen half as often. (Air strikes",
                "have their own separate multiplier.)");
        MULTIPLIER_AIR_STRIKES_COOLDOWN = mult(b,
                "multiplier_air_strikes_cooldown",
                "Scales the cooldown between air strikes, separately from the brain initiatives cooldown. 2.0 = half as often.");
        MULTIPLIER_BLOCK_INFECTION_SPEED = mult(b,
                "multiplier_block_infection_speed",
                "Scales how fast blocks get infected/converted by the fertile zone. 2.0 = spreads twice as fast.");
        MULTIPLIER_BRAIN_INFLUENCE_RADIUS = mult(b,
                "multiplier_brain_influence_radius",
                "Scales the brain's influence radius -- every rule that acts 'within the brain's radius'. Also sets the base",
                "reach of the block infection.");
        MULTIPLIER_BLOCK_INFECTION_REACH = mult(b,
                "multiplier_block_infection_reach",
                "Scales how far the fertile zone reaches relative to the brain's radius. 2.0 = the fertile zone can reach",
                "twice the brain's radius (per-stage multipliers still apply on top).");
        MULTIPLIER_LIMBO_RAIDERS = mult(b,
                "multiplier_limbo_raiders",
                "Scales how many limbo raiders appear during every raid phase. 2.0 = twice as many.");
        b.pop();

        b.push("values");
        AIR_STRIKES_APPEAR_AT_STAGE = b
                .comment("The brain stage (1-4) at which air strikes become available. Default: 4.")
                .defineInRange("air_strikes_appear_at_stage", 4, 1, 4);
        UNSTABLE_LIMBO_GRAVEL_HP_THRESHOLD = b
                .comment("A creature whose base max-health exceeds this is 'unstable' in Limbo (its placed gravel self-destructs",
                        "after the countdown). Default: 150.")
                .defineInRange("unstable_limbo_gravel_HP_threshold", 150, 1, 100000);
        GUARDS_SPAWNING_WITH_GENERAL_DARIUS = b
                .comment("If greater than 0, this many guards spawn alongside General Darius and are immediately enrolled by him.",
                        "Default: 0.")
                .defineInRange("guards_spawning_with_general_darius", 0, 0, 64);
        b.pop();

        SPEC = b.build();

        // Generic key -> value index (the 27 player-facing tunables, declaration order). Drift is internal/omitted.
        INDEX.put("creature_rocket_explosion_destroy_blocks", CREATURE_ROCKET_EXPLOSION_DESTROY_BLOCKS);
        INDEX.put("creature_rocket_set_blocks_on_fire", CREATURE_ROCKET_SET_BLOCKS_ON_FIRE);
        INDEX.put("air_strike_rocket_explosion_destroy_blocks", AIR_STRIKE_ROCKET_EXPLOSION_DESTROY_BLOCKS);
        INDEX.put("air_strike_rocket_set_blocks_on_fire", AIR_STRIKE_ROCKET_SET_BLOCKS_ON_FIRE);
        INDEX.put("emerald_virus_explosion_destroy_blocks", EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS);
        INDEX.put("emerald_virus_explosion_set_blocks_on_fire", EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE);
        INDEX.put("brain_node_extinguishes_fire", BRAIN_NODE_EXTINGUISHES_FIRE);
        INDEX.put("player_is_hostile", PLAYER_IS_HOSTILE);
        INDEX.put("emeraldgers_size_variation", EMERALDGERS_SIZE_VARIATION);
        INDEX.put("mega_expansion_mode", MEGA_EXPANSION_MODE);
        INDEX.put("general_darius_remains", GENERAL_DARIUS_REMAINS);
        INDEX.put("mega_brutal_mode", MEGA_BRUTAL_MODE);
        INDEX.put("player_spawn_in_village", PLAYER_SPAWN_IN_VILLAGE);
        INDEX.put("player_is_given_bell_at_spawn", PLAYER_IS_GIVEN_BELL_AT_SPAWN);
        INDEX.put("enable_block_infection", ENABLE_BLOCK_INFECTION);
        INDEX.put("multiplier_level_up_emerald_threshold", MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD);
        INDEX.put("multiplier_unit_spawn_rate", MULTIPLIER_UNIT_SPAWN_RATE);
        INDEX.put("multiplier_number_of_settlers", MULTIPLIER_NUMBER_OF_SETTLERS);
        INDEX.put("multiplier_brain_initiatives_cooldown", MULTIPLIER_BRAIN_INITIATIVES_COOLDOWN);
        INDEX.put("multiplier_air_strikes_cooldown", MULTIPLIER_AIR_STRIKES_COOLDOWN);
        INDEX.put("multiplier_block_infection_speed", MULTIPLIER_BLOCK_INFECTION_SPEED);
        INDEX.put("multiplier_brain_influence_radius", MULTIPLIER_BRAIN_INFLUENCE_RADIUS);
        INDEX.put("multiplier_block_infection_reach", MULTIPLIER_BLOCK_INFECTION_REACH);
        INDEX.put("multiplier_limbo_raiders", MULTIPLIER_LIMBO_RAIDERS);
        INDEX.put("air_strikes_appear_at_stage", AIR_STRIKES_APPEAR_AT_STAGE);
        INDEX.put("unstable_limbo_gravel_HP_threshold", UNSTABLE_LIMBO_GRAVEL_HP_THRESHOLD);
        INDEX.put("guards_spawning_with_general_darius", GUARDS_SPAWNING_WITH_GENERAL_DARIUS);
    }

    private static ForgeConfigSpec.DoubleValue mult(ForgeConfigSpec.Builder b, String key, String... comment) {
        return b.comment(comment).defineInRange(key, MULT_DEFAULT, MULT_MIN, MULT_MAX);
    }

    /** Push the loaded config into the systems that cache it. Called on config (re)load via ModConfigEvent. */
    public static void apply() {
        LimboDrift.overworldChance = DRIFT_OVERWORLD_CHANCE.get();
        LimboDrift.netherChance = DRIFT_NETHER_CHANCE.get();
        // Scale the brain's influence radius everywhere it is the source of truth. Bases are the vanilla defaults;
        // computed from the literal base each time so repeated reloads never compound. Detection-only ranges
        // (wither 200, miner 160) and the small tactical radii (immunity 10, rally 30) are intentionally left alone.
        double r = brainRadiusMult();
        BrainNodeEvents.INCOME_RADIUS = (int) Math.round(100.0 * r);
        BrainReflexes.RADIUS = 100.0 * r;
        GeneralDariusEntity.BRAIN_RADIUS = 100.0 * r;
        UpgradeEvents.RADIUS = 100.0 * r;
        NodeSpawns.GOLEM_ZONE_RADIUS = 100.0 * r;
        VillageEvents.GUARD_KILL_EMERALD_RANGE = 100.0 * r;
        FoundingManager.CHECK_RADIUS = 64.0 * r;
    }

    // --- convenience accessors (read the live value) ---
    public static boolean creatureRocketDestroysBlocks() { return CREATURE_ROCKET_EXPLOSION_DESTROY_BLOCKS.get(); }
    public static boolean creatureRocketSetsFire()        { return CREATURE_ROCKET_SET_BLOCKS_ON_FIRE.get(); }
    public static boolean airStrikeDestroysBlocks()       { return AIR_STRIKE_ROCKET_EXPLOSION_DESTROY_BLOCKS.get(); }
    public static boolean airStrikeSetsFire()             { return AIR_STRIKE_ROCKET_SET_BLOCKS_ON_FIRE.get(); }
    public static boolean emeraldVirusDestroysBlocks()    { return EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS.get(); }
    public static boolean emeraldVirusSetsFire()          { return EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE.get(); }
    public static boolean brainExtinguishesFire()         { return BRAIN_NODE_EXTINGUISHES_FIRE.get(); }
    public static boolean playerIsHostile()               { return PLAYER_IS_HOSTILE.get(); }
    public static boolean emeraldgerSizeVariation()       { return EMERALDGERS_SIZE_VARIATION.get(); }
    public static boolean megaExpansionMode()             { return MEGA_EXPANSION_MODE.get(); }
    public static boolean generalDariusRemains()          { return GENERAL_DARIUS_REMAINS.get(); }
    public static boolean megaBrutalMode()                { return MEGA_BRUTAL_MODE.get(); }
    public static boolean playerSpawnInVillage()          { return PLAYER_SPAWN_IN_VILLAGE.get(); }
    public static boolean playerGivenBellAtSpawn()        { return PLAYER_IS_GIVEN_BELL_AT_SPAWN.get(); }
    public static boolean blockInfectionEnabled()         { return ENABLE_BLOCK_INFECTION.get(); }

    public static double levelUpThresholdMult()  { return MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD.get(); }
    public static double unitSpawnRateMult()     { return MULTIPLIER_UNIT_SPAWN_RATE.get(); }
    public static double settlerCountMult()      { return MULTIPLIER_NUMBER_OF_SETTLERS.get(); }
    public static double brainInitiativeCooldownMult() { return MULTIPLIER_BRAIN_INITIATIVES_COOLDOWN.get(); }
    public static double airStrikeCooldownMult() { return MULTIPLIER_AIR_STRIKES_COOLDOWN.get(); }
    public static double infectionSpeedMult()    { return MULTIPLIER_BLOCK_INFECTION_SPEED.get(); }
    public static double brainRadiusMult()       { return MULTIPLIER_BRAIN_INFLUENCE_RADIUS.get(); }
    public static double infectionReachMult()    { return MULTIPLIER_BLOCK_INFECTION_REACH.get(); }
    public static double limboRaidersMult()      { return MULTIPLIER_LIMBO_RAIDERS.get(); }

    public static int airStrikesAppearAtStage()  { return AIR_STRIKES_APPEAR_AT_STAGE.get(); }
    public static int unstableLimboGravelHpThreshold() { return UNSTABLE_LIMBO_GRAVEL_HP_THRESHOLD.get(); }
    public static int guardsWithGeneralDarius()  { return GUARDS_SPAWNING_WITH_GENERAL_DARIUS.get(); }

    /** How many spawn events per day the unit-spawn-rate multiplier implies (>=1, rounded). */
    public static int spawnsPerDay() { return Math.max(1, (int) Math.round(unitSpawnRateMult())); }
}
