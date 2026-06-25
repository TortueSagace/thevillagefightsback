package com.example.villagefightsback;

import net.minecraft.world.level.pathfinder.PathType;

/**
 * Custom pathfinding type for the fake-cobblestone "smart door".
 *
 * In 1.21, {@code BlockPathTypes} was renamed to {@link PathType} and is a plain (non-extensible) vanilla
 * enum -- there is no Forge {@code BlockPathTypes.create(...)} hook in NeoForge to mint a custom value. So
 * instead of a dedicated type, allies share the vanilla {@link PathType#BLOCKED} marker: a block returns it
 * from {@code getBlockPathType} and the per-mob decision is made via {@code Mob.getPathfindingMalus(type)}.
 * {@code BLOCKED} defaults to malus -1.0 (BLOCKED) for every mob, so any mob that hasn't opted in won't route
 * through it. Allied mobs (villagers/iron golems/guards) call {@code setPathfindingMalus(ALLY_PASSABLE, 0)} so
 * the smart door becomes passable for them only.
 */
public final class ModPathTypes {
    /**
     * The path type our smart-door block reports, and that allies set to malus 0 to walk through. It maps to
     * vanilla {@link PathType#BLOCKED}, whose default malus is -1.0 (BLOCKED) for every mob unless it opts in.
     */
    public static final PathType ALLY_PASSABLE = PathType.BLOCKED;

    private ModPathTypes() {}

    /** Kept for call-site parity with the old custom-type registration (now a no-op). */
    public static void init() {}
}
