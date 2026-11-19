package com.example.villagefightsback.modules;

/**
 * The grouping a {@link Module} appears under, mirroring Quark's categories: the point is that a player who wants
 * to strip the mod down can look at one heading ("Creatures") and turn off everything under it rather than
 * hunting through keys.
 */
public enum ModuleCategory {
    MANAGEMENT("Management"),
    DEFENSE("Defense"),
    CREATURES("Creatures"),
    COMBAT("Combat"),
    WORLD("World"),
    CLIENT("Client");

    /** Human-readable heading, used by the log table and the {@code /thevillagefightsback modules} listing. */
    public final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }
}
