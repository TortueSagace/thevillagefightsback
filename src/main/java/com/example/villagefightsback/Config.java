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
 * default to {@code true} except the "off by default" switches called out in their comments.
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
    public static final ForgeConfigSpec.BooleanValue ENABLE_EMERALD_VIRUS_EXPLOSION;
    public static final ForgeConfigSpec.BooleanValue EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE;
    // --- feature switches ---
    public static final ForgeConfigSpec.BooleanValue BRAIN_NODE_EXTINGUISHES_FIRE;
    public static final ForgeConfigSpec.BooleanValue DEFEATED_BRAIN_NODE_SPAWN_SPIRIT;
    public static final ForgeConfigSpec.BooleanValue PLAYER_IS_HOSTILE;
    public static final ForgeConfigSpec.BooleanValue EMERALDGERS_SIZE_VARIATION;
    public static final ForgeConfigSpec.BooleanValue MEGA_EXPANSION_MODE;
    public static final ForgeConfigSpec.BooleanValue GENERAL_DARIUS_REMAINS;
    public static final ForgeConfigSpec.BooleanValue DARIUS_CAN_BREAK_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue DARIUS_INFLICTS_OBLITERATION;
    public static final ForgeConfigSpec.BooleanValue REVERSED_GRAVITY_FLIPS_SCREEN;
    public static final ForgeConfigSpec.BooleanValue UNSTICK_TEST_MODE;
    public static final ForgeConfigSpec.BooleanValue INFESTED_NEEDS_BRAIN_NODE;
    public static final ForgeConfigSpec.BooleanValue MONOCHROME_GREEN;
    public static final ForgeConfigSpec.BooleanValue MEGA_BRUTAL_MODE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_SPAWN_IN_VILLAGE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_IS_GIVEN_BELL_AT_SPAWN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_INFECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MOB_INFECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_EMERALD_INFESTATION;
    public static final ForgeConfigSpec.BooleanValue EMERALD_MOBS_ARE_HOSTILE_TO_PLAYER;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_EMERALD_MOBS_SPAWNED_BY_BRAIN;
    public static final ForgeConfigSpec.IntValue EMERALD_MOBS_MAX_NUMBER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ORGANIC_MASS_SYSTEM;
    public static final ForgeConfigSpec.DoubleValue ORGANIC_ARMY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ORGANIC_SPAWN_BUDGET_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue GUARDS_DROP_FIRE_GRENADE_UPON_DEATH;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHUNK_PORTALS;
    public static final ForgeConfigSpec.BooleanValue MCA_COMPAT;
    public static final ForgeConfigSpec.IntValue DIFFICULTY;
    // --- multipliers (float, default 1.0, lower-bounded) ---
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue MULTIPLIER_UNIT_SPAWN_FREQUENCY;
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
        ENABLE_EMERALD_VIRUS_EXPLOSION = b
                .comment("Master switch for the emerald-virus death explosion. If false, a creature that dies carrying the",
                        "emerald virus does NOT explode at all (no blast, no entity damage, no fire) -- the two options below",
                        "then have no effect. The mite/emeraldger spawn and emerald loot still happen. Default: false (nerf).")
                .define("enable_emerald_virus_explosion", false);
        EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS = b
                .comment("If true, the explosion of a creature that dies carrying the emerald virus breaks blocks.",
                        "The blast still hurts units either way. Default: false.")
                .define("emerald_virus_explosion_destroy_blocks", false);
        EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE = b
                .comment("If true, the emerald-virus death explosion can set blocks on fire. Default: false.")
                .define("emerald_virus_explosion_set_blocks_on_fire", false);
        b.pop();

        b.push("features");
        BRAIN_NODE_EXTINGUISHES_FIRE = b
                .comment("If true, a brain node will (free, on a 1-minute cooldown, at any stage) clear ALL vanilla",
                        "fire (normal + soul fire) within its influence radius. This mod adds no fire of its own; other",
                        "mods' custom fire is left untouched.")
                .define("brain_node_extinguishes_fire", true);
        DEFEATED_BRAIN_NODE_SPAWN_SPIRIT = b
                .comment("If true, destroying a Village Brain Node (any non-silk-touch break) unleashes the Vindictive",
                        "Village Spirit boss, with its emerge animation and warning. If false, the node can be destroyed",
                        "without summoning the boss. Default: true.")
                .define("defeated_brain_node_spawn_spirit", true);
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
        DARIUS_CAN_BREAK_BLOCKS = b
                .comment("If true, General Darius chews through terrain: he breaks soft blocks on contact, digs down to",
                        "a target below him and tunnels up to one above him. Turn it off and he leaves the world alone",
                        "and paths around it like anything else -- his pathfinder is told about the change too, so he",
                        "does not simply walk into the rock he can no longer break. Default: true.")
                .define("darius_can_break_blocks", true);
        REVERSED_GRAVITY_FLIPS_SCREEN = b
                .comment("If true, the five seconds of reversed gravity that follow a Special Sauce holder falling out of",
                        "the world also turn every player's view upside down. The physics are unaffected either way -- this",
                        "only controls the camera. Set false if a 180 degree world roll makes anyone on the server queasy.",
                        "Default: true.")
                .define("reversed_gravity_flips_screen", true);
        UNSTICK_TEST_MODE = b
                .comment("TESTING ONLY. A villager or guard that has not left a 1x1 spot is normally rescued by the",
                        "brain node after 30 seconds if it is stuck in a pit, or after 60 seconds whatever the reason.",
                        "Set this true and both clocks drop to a few seconds, so the rescue can be watched without",
                        "standing around waiting for it. Leave it false in a real world: it makes the mod quick to",
                        "decide that a villager doing something slow is stuck. Default: false.")
                .define("unstick_test_mode", false);
        INFESTED_NEEDS_BRAIN_NODE = b
                .comment("If true, infested paths and fertile grass only spread blocks while they are within the",
                        "100-block reach of a brain node that has reached stage 2. This keeps a converted zone",
                        "roughly inside the village's own radius instead of creeping across the map, and stops any",
                        "conversion at all while the node is still stage 1. Set false and both spread on their own",
                        "wherever they happen to be, which is the pre-1.5.2 behaviour. Default: true.")
                .define("infested_paths_need_brain_node", true);

        MONOCHROME_GREEN = b
                .comment("If true, everything the infection converts from now on is GREEN, as it was before the",
                        "colour sets were added. The four other colours stay registered and stay in the VFB:",
                        "blocks creative tab -- this only stops the infection CHOOSING them: a new infested path",
                        "is laid green, and a conversion inherits green rather than its source's colour.",
                        "Blocks already placed keep the colour they were placed with; nothing rewrites the world.",
                        "Default: false.")
                .define("monochrome_green_fertile_blocks", false);

        DARIUS_INFLICTS_OBLITERATION = b
                .comment("If true, every one of General Darius's blows marks its victim with Obliteration, and a stage-4 brain",
                        "node raises an Obliteration Furnace beside itself and pays 1 emerald per minute to keep it lit (never",
                        "so much that the village drops back to stage 3). Together those mean a creature Darius kills near the",
                        "village is struck off the spawn tables for as long as the village can afford the fuel. Players are",
                        "never marked and can never be obliterated, whatever this is set to. Set false and Darius is simply a",
                        "hard fighter: no mark, and the node neither builds nor fuels a furnace. Default: true.")
                .define("darius_inflicts_obliteration", true);
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
        ENABLE_MOB_INFECTION = b
                .comment("Master switch for MOB infection (the 20 emerald units). If false, NO emerald unit can ever spawn:",
                        "no infection-conversion of a slain mob, no brain summon, not even a spawn egg. Default: true.")
                .define("enable_mob_infection", true);
        ENABLE_EMERALD_INFESTATION = b
                .comment("Switch for the EMERALD faction's block infestation: the lime 'infested path' laid below a converted",
                        "unit and its slow fertile/rich spread. This is the emerald-faction counterpart of enable_block_infection",
                        "(which is the VILLAGE brain node's own fertile spread) and is fully independent of it: turn this off to",
                        "keep emerald mobs but stop them altering the world. Default: true.")
                .define("enable_emerald_infestation", true);
        EMERALD_MOBS_ARE_HOSTILE_TO_PLAYER = b
                .comment("If true, emerald units treat the player as a target (a non-creative/spectator player who is an Enemy",
                        "of the Village). If false, emerald units never attack the player at all. Default: false.")
                .define("emerald_mobs_are_hostile_to_player", false);
        MULTIPLIER_EMERALD_MOBS_SPAWNED_BY_BRAIN = mult(b,
                "multiplier_emerald_mobs_spawned_by_brain",
                "Scales the emerald scouts the brain summons: base 2 cats per settlement + 1 bat per sunset, times this.",
                "e.g. 2 -> 4 cats and 2 bats. Default: 1.");
        EMERALD_MOBS_MAX_NUMBER = b
                .comment("Soft LOCAL cap on emerald units (mites included) within a player's active range of a spawn, to limit",
                        "lag. Emeralds far from any player don't count. When an automatic spawn would exceed it the unit does",
                        "NOT appear; instead the nearest non-mite emerald gains its max HP (a conversion's victim just dies, and",
                        "its infested path still appears). A spawn egg / summon at the cap tells the player. 0 = unlimited. Default: 50.")
                .defineInRange("emerald_mobs_max_number", 50, 0, 100000);
        ENABLE_ORGANIC_MASS_SYSTEM = b
                .comment("ORGANIC MASS system (alternative to convert-on-kill). If true: an emerald unit's kills do NOT convert",
                        "the victim into an emerald unit. Instead an infested path is laid below the victim and the victim's max",
                        "HP is added as 'organic mass' to the nearest brain node, which banks it and spends it to spawn its own",
                        "emerald army at infested blocks (a decision tree picks when/what). Organic mass is shown next to the brain's",
                        "emerald count and NEVER affects the brain's level/emerald economy. If false: keep the classic 1.1.1",
                        "convert-on-kill behavior. Default: true.")
                .define("enable_organic_mass_system", true);
        ORGANIC_ARMY_MULTIPLIER = mult(b,
                "organic_army_multiplier",
                "Scales the standing organic army a brain may field: cap = emerald_mobs_max_number x this (default 50).",
                "e.g. 2 -> up to 100 emerald units. Default: 1.");
        ORGANIC_SPAWN_BUDGET_MULTIPLIER = mult(b,
                "organic_spawn_budget_multiplier",
                "Scales how much of its banked organic mass a brain spends per decision (more = more aggressive bursts).",
                "Default: 1.");
        GUARDS_DROP_FIRE_GRENADE_UPON_DEATH = b
                .comment("If true, a stage-3 guard slain by a hostile/boss-minion goes out in a TNT-strength, fire-spreading",
                        "blast (a 'fire grenade'). If false, guards die quietly. Default: true.")
                .define("guards_drop_fire_grenade_upon_death", true);
        ENABLE_CHUNK_PORTALS = b
                .comment("Master switch for CHUNK PORTALS (work in progress, v1.2.1). When ON: once a village brain reaches",
                        "stage 3, ~1% of nearby overworld chunks turn into 4-wall portals that show, and lead into, the Limbo.",
                        "Default: false (off for now). Turning this on applies portals immediately to any stage-3-or-higher",
                        "brain node currently in reach (loaded); turning it off only stops NEW portals -- existing ones persist.")
                .define("enable_chunk_portals", false);
        MCA_COMPAT = b
                .comment("Minecraft Comes Alive (MCA Reborn) integration. Has NO effect unless MCA is installed. When ON and",
                        "MCA is present, every villager this mod spawns (settlers, brain-node reinforcements, staged spawn eggs)",
                        "is spawned as an MCA human instead of a vanilla villager, so the village is uniformly MCA rather than a",
                        "mix. MCA villagers extend the vanilla villager, so they take the same roles, weapons, walls and virus.",
                        "Turn OFF to spawn plain vanilla villagers alongside MCA's. Default: true.")
                .define("mca_compat", true);
        DIFFICULTY = b
                .comment("How the village TREATS YOU, picked on the difficulty screen when a world is created.",
                        "0 = Peaceful  -- permanent Hero of the Village; you can never become its Enemy.",
                        "1 = Neutral   -- the village judges you on what you do. The recommended setting.",
                        "2 = Hostile   -- permanent Enemy of the Village; you can never become its Hero.",
                        "3 = Nightmare -- Hostile, plus a raid sent to hunt you down every sunset.",
                        "On Peaceful and Hostile the forced status cannot be cured, by milk or by dying. This is separate from",
                        "the GAME MODE (what spreads and spawns); the two are chosen on two screens. Default: 1.")
                .defineInRange("difficulty", 1, 0, 3);
        b.pop();

        b.push("multipliers");
        MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD = mult(b,
                "multiplier_level_up_emerald_threshold",
                "Scales the emerald cost for the brain node to reach each stage. 2.0 = every stage costs twice as much.");
        MULTIPLIER_UNIT_SPAWN_FREQUENCY = mult(b,
                "multiplier_unit_spawn_frequency",
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
                .comment("A creature whose base max-health reaches this is 'unstable' in Limbo (its placed gravel self-destructs",
                        "after the countdown). Default: 50.")
                .defineInRange("unstable_limbo_gravel_HP_threshold", 50, 1, 100000);
        GUARDS_SPAWNING_WITH_GENERAL_DARIUS = b
                .comment("If greater than 0, this many guards spawn alongside General Darius and are immediately enrolled by him.",
                        "Default: 0.")
                .defineInRange("guards_spawning_with_general_darius", 0, 0, 64);
        b.pop();

        SPEC = b.build();

        // Generic key -> value index (the 37 player-facing tunables, declaration order). Drift is internal/omitted.
        INDEX.put("creature_rocket_explosion_destroy_blocks", CREATURE_ROCKET_EXPLOSION_DESTROY_BLOCKS);
        INDEX.put("creature_rocket_set_blocks_on_fire", CREATURE_ROCKET_SET_BLOCKS_ON_FIRE);
        INDEX.put("air_strike_rocket_explosion_destroy_blocks", AIR_STRIKE_ROCKET_EXPLOSION_DESTROY_BLOCKS);
        INDEX.put("air_strike_rocket_set_blocks_on_fire", AIR_STRIKE_ROCKET_SET_BLOCKS_ON_FIRE);
        INDEX.put("enable_emerald_virus_explosion", ENABLE_EMERALD_VIRUS_EXPLOSION);
        INDEX.put("emerald_virus_explosion_destroy_blocks", EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS);
        INDEX.put("emerald_virus_explosion_set_blocks_on_fire", EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE);
        INDEX.put("brain_node_extinguishes_fire", BRAIN_NODE_EXTINGUISHES_FIRE);
        INDEX.put("defeated_brain_node_spawn_spirit", DEFEATED_BRAIN_NODE_SPAWN_SPIRIT);
        INDEX.put("player_is_hostile", PLAYER_IS_HOSTILE);
        INDEX.put("emeraldgers_size_variation", EMERALDGERS_SIZE_VARIATION);
        INDEX.put("mega_expansion_mode", MEGA_EXPANSION_MODE);
        INDEX.put("general_darius_remains", GENERAL_DARIUS_REMAINS);
        INDEX.put("darius_can_break_blocks", DARIUS_CAN_BREAK_BLOCKS);
        INDEX.put("darius_inflicts_obliteration", DARIUS_INFLICTS_OBLITERATION);
        INDEX.put("mega_brutal_mode", MEGA_BRUTAL_MODE);
        INDEX.put("player_spawn_in_village", PLAYER_SPAWN_IN_VILLAGE);
        INDEX.put("player_is_given_bell_at_spawn", PLAYER_IS_GIVEN_BELL_AT_SPAWN);
        INDEX.put("unstick_test_mode", UNSTICK_TEST_MODE);
        INDEX.put("infested_paths_need_brain_node", INFESTED_NEEDS_BRAIN_NODE);
        INDEX.put("monochrome_green_fertile_blocks", MONOCHROME_GREEN);
        INDEX.put("enable_block_infection", ENABLE_BLOCK_INFECTION);
        INDEX.put("enable_mob_infection", ENABLE_MOB_INFECTION);
        INDEX.put("enable_emerald_infestation", ENABLE_EMERALD_INFESTATION);
        INDEX.put("emerald_mobs_are_hostile_to_player", EMERALD_MOBS_ARE_HOSTILE_TO_PLAYER);
        INDEX.put("multiplier_emerald_mobs_spawned_by_brain", MULTIPLIER_EMERALD_MOBS_SPAWNED_BY_BRAIN);
        INDEX.put("emerald_mobs_max_number", EMERALD_MOBS_MAX_NUMBER);
        INDEX.put("enable_organic_mass_system", ENABLE_ORGANIC_MASS_SYSTEM);
        INDEX.put("organic_army_multiplier", ORGANIC_ARMY_MULTIPLIER);
        INDEX.put("organic_spawn_budget_multiplier", ORGANIC_SPAWN_BUDGET_MULTIPLIER);
        INDEX.put("guards_drop_fire_grenade_upon_death", GUARDS_DROP_FIRE_GRENADE_UPON_DEATH);
        INDEX.put("enable_chunk_portals", ENABLE_CHUNK_PORTALS);
        INDEX.put("mca_compat", MCA_COMPAT);
        INDEX.put("difficulty", DIFFICULTY);
        INDEX.put("multiplier_level_up_emerald_threshold", MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD);
        INDEX.put("multiplier_unit_spawn_frequency", MULTIPLIER_UNIT_SPAWN_FREQUENCY);
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

    // --- per-world game mode ------------------------------------------------------------------------
    // This is a COMMON config, i.e. one shared file for every world. The mode screen shown when a world is
    // created ({@link WorldMode} / {@link WorldModeData}) must not let one world silently re-configure another,
    // so the three infection switches are overridden IN MEMORY for as long as that world is loaded, and the
    // file's own values are put back when the server stops. The .toml itself is never written by this path.

    /** The .toml's own {block, mob, infestation} values, held while a world overrides them; null when none is. */
    private static boolean[] infectionSnapshot;
    private static Integer difficultySnapshot;

    /** Remember the file's infection flags (server start), so {@link #restoreInfectionFlags()} can put them back. */
    public static void snapshotInfectionFlags() {
        if (infectionSnapshot == null) {
            infectionSnapshot = new boolean[] {
                    ENABLE_BLOCK_INFECTION.get(), ENABLE_MOB_INFECTION.get(), ENABLE_EMERALD_INFESTATION.get() };
        }
        // The DIFFICULTY rides along, and must. It is per-world state living in a process-global config, so
        // without this a world created after a Nightmare one inherits Nightmare until its own answer arrives --
        // and the login-time code that places the brain node and takes the bell runs inside exactly that window.
        if (difficultySnapshot == null) difficultySnapshot = DIFFICULTY.get();
    }

    /** Put the file's infection flags back (server stop), so the next world starts from the file, not the last world. */
    public static void restoreInfectionFlags() {
        if (infectionSnapshot == null) return;
        ENABLE_BLOCK_INFECTION.set(infectionSnapshot[0]);
        ENABLE_MOB_INFECTION.set(infectionSnapshot[1]);
        ENABLE_EMERALD_INFESTATION.set(infectionSnapshot[2]);
        infectionSnapshot = null;
        if (difficultySnapshot != null) {
            DIFFICULTY.set(difficultySnapshot);
            difficultySnapshot = null;
        }
    }

    /** Push a world's stored mode over the live values. In memory only -- the .toml file is not touched. */
    public static void applyInfectionFlags(boolean block, boolean mob, boolean infestation) {
        ENABLE_BLOCK_INFECTION.set(block);
        ENABLE_MOB_INFECTION.set(mob);
        ENABLE_EMERALD_INFESTATION.set(infestation);
    }

    /** A config command changed one of the three: keep the snapshot in step so the stop-restore cannot undo it. */
    public static void noteInfectionFlag(String key, boolean value) {
        if (infectionSnapshot == null) return;
        switch (key) {
            case "enable_block_infection" -> infectionSnapshot[0] = value;
            case "enable_mob_infection" -> infectionSnapshot[1] = value;
            case "enable_emerald_infestation" -> infectionSnapshot[2] = value;
            default -> { }
        }
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
    public static boolean emeraldVirusExplosion()         { return ENABLE_EMERALD_VIRUS_EXPLOSION.get(); }
    public static boolean emeraldVirusDestroysBlocks()    { return EMERALD_VIRUS_EXPLOSION_DESTROY_BLOCKS.get(); }
    public static boolean emeraldVirusSetsFire()          { return EMERALD_VIRUS_EXPLOSION_SET_BLOCKS_ON_FIRE.get(); }
    public static boolean brainExtinguishesFire()         { return BRAIN_NODE_EXTINGUISHES_FIRE.get(); }
    public static boolean spawnSpiritOnDefeat()           { return DEFEATED_BRAIN_NODE_SPAWN_SPIRIT.get(); }
    public static boolean playerIsHostile()               { return PLAYER_IS_HOSTILE.get(); }
    public static boolean emeraldgerSizeVariation()       { return EMERALDGERS_SIZE_VARIATION.get(); }
    public static boolean megaExpansionMode()             { return MEGA_EXPANSION_MODE.get(); }
    public static boolean generalDariusRemains()          { return GENERAL_DARIUS_REMAINS.get(); }
    public static boolean dariusCanBreakBlocks()          { return DARIUS_CAN_BREAK_BLOCKS.get(); }
    public static boolean dariusInflictsObliteration()    { return DARIUS_INFLICTS_OBLITERATION.get(); }
    public static boolean megaBrutalMode()                { return MEGA_BRUTAL_MODE.get(); }
    public static boolean playerSpawnInVillage()          { return PLAYER_SPAWN_IN_VILLAGE.get(); }
    public static boolean playerGivenBellAtSpawn()        { return PLAYER_IS_GIVEN_BELL_AT_SPAWN.get(); }
    public static boolean unstickTestMode()               { return UNSTICK_TEST_MODE.get(); }
    public static boolean infestedNeedsBrainNode()        { return INFESTED_NEEDS_BRAIN_NODE.get(); }
    public static boolean monochromeGreen()               { return MONOCHROME_GREEN.get(); }
    public static boolean blockInfectionEnabled()         { return ENABLE_BLOCK_INFECTION.get(); }
    public static boolean mobInfectionEnabled()           { return ENABLE_MOB_INFECTION.get(); }
    public static boolean emeraldInfestationEnabled()     { return ENABLE_EMERALD_INFESTATION.get(); }
    /** Emerald MOBS spawn only when BOTH the mob-infection master gate AND the emerald-infestation toggle are on.
     *  Turning off enable_emerald_infestation therefore stops every emerald mob (in addition to the block spread). */
    public static boolean emeraldMobsEnabled()            { return ENABLE_MOB_INFECTION.get() && ENABLE_EMERALD_INFESTATION.get(); }
    public static boolean emeraldMobsHostileToPlayer()    { return EMERALD_MOBS_ARE_HOSTILE_TO_PLAYER.get(); }
    public static double emeraldBrainSpawnMult()          { return MULTIPLIER_EMERALD_MOBS_SPAWNED_BY_BRAIN.get(); }
    public static int emeraldMobsMaxNumber()              { return EMERALD_MOBS_MAX_NUMBER.get(); }
    public static boolean organicMassEnabled()            { return ENABLE_ORGANIC_MASS_SYSTEM.get(); }
    public static int difficulty()                        { return DIFFICULTY.get(); }
    public static double organicArmyMultiplier()          { return ORGANIC_ARMY_MULTIPLIER.get(); }
    public static double organicSpawnBudgetMultiplier()   { return ORGANIC_SPAWN_BUDGET_MULTIPLIER.get(); }
    public static boolean guardsDropFireGrenadeUponDeath() { return GUARDS_DROP_FIRE_GRENADE_UPON_DEATH.get(); }
    public static boolean chunkPortalsEnabled()           { return ENABLE_CHUNK_PORTALS.get(); }

    public static double levelUpThresholdMult()  { return MULTIPLIER_LEVEL_UP_EMERALD_THRESHOLD.get(); }
    public static double unitSpawnFrequencyMult()     { return MULTIPLIER_UNIT_SPAWN_FREQUENCY.get(); }
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
    public static int spawnsPerDay() { return Math.max(1, (int) Math.round(unitSpawnFrequencyMult())); }
}
