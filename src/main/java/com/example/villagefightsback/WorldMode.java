package com.example.villagefightsback;

/**
 * The three game modes offered by the mode screen that opens once, the first time a player enters a
 * freshly created world (see {@link WorldModeEvents} / {@link com.example.villagefightsback.client.ModeSelectScreen}).
 *
 * <p>A mode is nothing more than a preset of the three infection master switches in {@link Config}; picking one
 * stores those three booleans in the world's own {@link WorldModeData} and pushes them over the .toml values for
 * as long as that world is loaded. Everything else about the mod is untouched, and the player can still flip the
 * individual switches afterwards with {@code /thevillagefightsback config <key> <value>}.
 *
 * <p>Note the mod has TWO independent block-spread systems: {@code enable_block_infection} is the VILLAGE brain
 * node's fertile spread, {@code enable_emerald_infestation} is the EMERALD faction's infested paths. The latter
 * also hard-gates every emerald mob ({@link Config#emeraldMobsEnabled()}), so it has to ride along with the mob
 * switch rather than with the block one.
 */
public enum WorldMode {
    /** No infection at all: the village still evolves, arms itself and fights, but nothing spreads or converts. */
    NORMAL(0, "Normal Village", false, false, false),
    /** The village brain node's fertile spread only; no emerald units are ever created. */
    BLOCK(1, "Village with Block Infection", true, false, false),
    /** The full game: fertile spread, emerald units, and their infested paths. */
    BLOCK_AND_MOB(2, "Village with Block + Mob Infection", true, true, true);

    private final int id;
    private final String title;
    private final boolean blockInfection;
    private final boolean mobInfection;
    private final boolean emeraldInfestation;

    WorldMode(int id, String title, boolean blockInfection, boolean mobInfection, boolean emeraldInfestation) {
        this.id = id;
        this.title = title;
        this.blockInfection = blockInfection;
        this.mobInfection = mobInfection;
        this.emeraldInfestation = emeraldInfestation;
    }

    public int id() { return id; }
    public String title() { return title; }
    public boolean blockInfection() { return blockInfection; }
    public boolean mobInfection() { return mobInfection; }
    public boolean emeraldInfestation() { return emeraldInfestation; }

    /** The mode with this id, or null if the id is not one of the three (never trust a number off the network). */
    public static WorldMode byId(int id) {
        for (WorldMode m : values()) if (m.id == id) return m;
        return null;
    }

    /** The mode whose preset matches these three flags exactly, or null if the player has since customised them. */
    public static WorldMode matching(boolean block, boolean mob, boolean infestation) {
        for (WorldMode m : values()) {
            if (m.blockInfection == block && m.mobInfection == mob && m.emeraldInfestation == infestation) return m;
        }
        return null;
    }
}
