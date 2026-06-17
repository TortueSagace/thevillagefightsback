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

    /**
     * General Darius is the village's SUMMONED CHAMPION (a stage-4 brain reflex). He extends {@code Monster}, so
     * he is technically an {@code Enemy}, but the village must treat him as one of its own and never target,
     * flee, shoot, or harm him. Every village-side threat scan excludes him with this check. (The boss faction --
     * the unleashed brain node and its vindictive minions -- is NOT filtered here: it rightly fights Darius.)
     */
    public static boolean isVillageChampion(Entity e) {
        return e instanceof GeneralDariusEntity;
    }

    /** The "limbo" faction: creatures native to Limbo -- the Limbo Phantom and the Limbo raiders (pillager /
     *  vindicator / evoker / ravager / vex). They never attack one another, so phantoms and raiders fight the
     *  village side by side instead of each other. */
    public static boolean isLimbo(Entity e) {
        return e instanceof LimboPhantomEntity || e instanceof LimboRaider;
    }
}
