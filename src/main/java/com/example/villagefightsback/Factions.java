package com.example.villagefightsback;

import net.minecraft.world.entity.Entity;

/**
 * The "infected" faction: the unleashed Village Brain Node boss and the vindictive guards it makes. Members
 * never target or harm one another; everything else is fair game. Used by the boss targeting, the vindictive
 * guard combat goal, and the friendly-fire / change-target event hooks in {@link UnleashedManager}.
 */
public final class Factions {
    private Factions() {}

    /** True for the unleashed brainNode boss and its minions (vindictive guards + vindictive gigachads). */
    public static boolean isInfected(Entity e) {
        return e instanceof UnleashedBrainNodeEntity
                || e instanceof VindictiveGuardEntity
                || e instanceof VindictiveGigachadEntity;
    }

    /** The "limbo" faction: creatures native to Limbo -- the Limbo Phantom and the Limbo raiders (pillager /
     *  vindicator / evoker / ravager / vex). They never attack one another, so phantoms and raiders fight the
     *  village side by side instead of each other. */
    public static boolean isLimbo(Entity e) {
        return e instanceof LimboPhantomEntity || e instanceof LimboRaider;
    }
}
