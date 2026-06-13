package com.example.villagergunners;

import net.minecraft.world.entity.Entity;

/**
 * The "infected" faction: the unleashed Village Gravemind boss and the vindictive guards it makes. Members
 * never target or harm one another; everything else is fair game. Used by the boss targeting, the vindictive
 * guard combat goal, and the friendly-fire / change-target event hooks in {@link UnleashedManager}.
 */
public final class Factions {
    private Factions() {}

    /** True for the unleashed gravemind boss and its minions (vindictive guards + vindictive gigachads). */
    public static boolean isInfected(Entity e) {
        return e instanceof UnleashedGravemindEntity
                || e instanceof VindictiveGuardEntity
                || e instanceof VindictiveGigachadEntity;
    }

    /** The "limbo" faction: creatures native to Limbo (the Limbo Phantom). They never attack one another. */
    public static boolean isLimbo(Entity e) {
        return e instanceof LimboPhantomEntity;
    }
}
