package com.example.villagefightsback;

/**
 * Marker for every member of the EMERALD faction (the "mob infection" units in v1.0.3): emerald cow, zombie,
 * wither, etc. Implemented by each {@code EmeraldX} entity class. Faction membership is tested with
 * {@link Factions#isEmerald(net.minecraft.world.entity.Entity)} (an {@code instanceof} on this interface).
 *
 * <p>Emerald units share one behaviour set, wired in via {@link EmeraldMobs}: they hunt everything that is not
 * spared (see {@link EmeraldMobs#isHostileTarget}); a normally-PASSIVE species ({@link #emeraldPassive()} true)
 * gains a melee attack dealing 50% of its max health; and every hit they land inflicts Emerald Infection.
 */
public interface EmeraldUnit {
    /** True for species that have no vanilla attack (cow, sheep, bat, ...): they get a 50%-max-HP melee attack. */
    default boolean emeraldPassive() { return false; }
}
