package com.example.villagefightsback;

import net.minecraft.world.level.pathfinder.BlockPathTypes;

/**
 * Custom pathfinding type for the fake-cobblestone "smart door".
 *
 * In 1.20.1, WalkNodeEvaluator calls a block's getBlockPathType hook with mob == null, so a block
 * can't tell allies from enemies there. Instead, the fake-cobblestone block returns this single type
 * for everyone, and the per-mob decision is made via {@code Mob.getPathfindingMalus(type)}: this type
 * defaults to malus -1 (BLOCKED), so any mob that hasn't opted in won't route through it. Allied mobs
 * (villagers/iron golems/guards) call {@code setPathfindingMalus(ALLY_PASSABLE, 0)} so it becomes
 * passable for them only.
 */
public final class ModPathTypes {
    /** Default malus -1.0 => BLOCKED for every mob unless it overrides the malus to >= 0. */
    public static final BlockPathTypes ALLY_PASSABLE =
            BlockPathTypes.create("gobacktoyourworld:ally_passable", -1.0F);

    private ModPathTypes() {}

    /** Force class-load (and thus the create() above) early, from the mod constructor. */
    public static void init() {}
}
