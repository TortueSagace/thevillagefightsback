package com.example.villagergunners;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Global reinforcement-flare rally state (one rally at a time per server). A fighting defender that is
 * swarmed fires a flare straight up and records the rally point here; idle defenders read it and run
 * toward it. A 30-second cooldown keeps flares from being spammed, and the rally auto-expires.
 */
public final class FlareRally {
    private FlareRally() {}

    public static final long COOLDOWN_TICKS = 600; // 30s minimum between flares (global)
    public static final long DURATION_TICKS = 600; // 30s the rally stays active for responders

    private static ResourceKey<Level> dimension;
    private static double x, y, z;
    private static long flareTime = Long.MIN_VALUE;

    /** True if enough time has passed since the last flare anywhere to fire a new one. */
    public static boolean canFire(long now) {
        return now - flareTime >= COOLDOWN_TICKS;
    }

    /** Record a new flare/rally at the given position in the given level. */
    public static void set(Level level, double px, double py, double pz, long now) {
        dimension = level.dimension();
        x = px;
        y = py;
        z = pz;
        flareTime = now;
    }

    /** Game time the current flare was fired (responders react to each distinct flare once). */
    public static long flareTime() {
        return flareTime;
    }

    /** True if there is an active (non-expired) rally in the given level. */
    public static boolean isActive(Level level, long now) {
        return dimension != null && level.dimension() == dimension && now - flareTime < DURATION_TICKS;
    }

    public static double x() { return x; }
    public static double y() { return y; }
    public static double z() { return z; }
}
