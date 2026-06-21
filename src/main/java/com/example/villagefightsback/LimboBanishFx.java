package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The "banishment" effect played where a creature's hitbox was when it is sent to Limbo: a column of big
 * black spirals (large smoke) climbing the hitbox, soul particles rising through it, and an enderman-style
 * teleport swirl -- animated for a few seconds, then gone. The zone is sized to the creature's hitbox, so a
 * bigger creature leaves a bigger swirl. Server-driven (particles are broadcast to nearby players), so it
 * keeps playing after the creature itself has left.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboBanishFx {
    private LimboBanishFx() {}

    private static final int DURATION = 60;   // ~3 seconds
    private static final int ARMS = 3;        // spiral arms
    private static final int HEIGHT_STEPS = 8;
    private static final int MAX_ACTIVE = 16; // bound the particle load when many are banished at once

    /** Lime spiral colour for the emerald-conversion variant (black smoke at the base -> lime up the column). */
    private static final net.minecraft.core.particles.DustParticleOptions LIME_DUST =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.40F, 1.0F, 0.25F), 1.5F);

    private record Fx(ServerLevel level, double cx, double bottom, double cz, double radius, double height, long end,
                      boolean lime) {}
    private static final List<Fx> ACTIVE = new ArrayList<>();

    /** Begin the (black) banishment swirl where {@code e}'s hitbox currently is. */
    public static void start(Entity e) { add(e, false); }

    /** The LIME variant played when an emerald infection CONVERTS a unit (v1.0.3): black smoke at the base rising
     *  into lime, with happy-villager sparkles. Call just BEFORE the original creature is removed. */
    public static void startLime(Entity e) { add(e, true); }

    /** The LIME swirl at a fixed spot (no entity hitbox) -- e.g. on the brain node when it spawns a unit. */
    public static void startLimeAt(ServerLevel sl, double cx, double bottom, double cz) {
        if (ACTIVE.size() >= MAX_ACTIVE) return;
        ACTIVE.add(new Fx(sl, cx, bottom, cz, 0.7, 1.6, sl.getGameTime() + DURATION, true));
    }

    private static void add(Entity e, boolean lime) {
        if (!(e.level() instanceof ServerLevel sl) || ACTIVE.size() >= MAX_ACTIVE) return;
        AABB b = e.getBoundingBox();
        double radius = Math.max(0.6, Math.max(b.getXsize(), b.getZsize()) * 0.65);
        double height = Math.max(1.2, b.getYsize());
        ACTIVE.add(new Fx(sl, (b.minX + b.maxX) / 2.0, b.minY, (b.minZ + b.maxZ) / 2.0,
                radius, height, sl.getGameTime() + DURATION, lime));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            long remaining = fx.end - fx.level.getGameTime();
            if (remaining <= 0) { it.remove(); continue; }
            play(fx, DURATION - remaining); // age in ticks
        }
    }

    private static void play(Fx fx, long age) {
        ServerLevel sl = fx.level;
        double phase = age * 0.30; // rotation speed (radians/tick)

        // 1. Big black spiral: a helix of large smoke climbing the hitbox, widening toward the top.
        for (int s = 0; s < HEIGHT_STEPS; s++) {
            double frac = s / (double) (HEIGHT_STEPS - 1);
            double y = fx.bottom + frac * fx.height;
            double r = fx.radius * (0.30 + 0.70 * frac);
            for (int a = 0; a < ARMS; a++) {
                double ang = phase + a * (2.0 * Math.PI / ARMS) + frac * 2.0 * Math.PI; // one turn over the height
                double x = fx.cx + r * Math.cos(ang);
                double z = fx.cz + r * Math.sin(ang);
                // count 0 -> one particle at exactly (x,y,z) with this velocity (gentle inward + upward swirl).
                // Lime variant: black smoke at the base transitions to lime dust up the column.
                if (fx.lime && frac > 0.45) {
                    force(sl, LIME_DUST, x, y, z, 0,
                            -Math.cos(ang) * 0.02, 0.05, -Math.sin(ang) * 0.02, 1.0);
                } else {
                    force(sl, ParticleTypes.LARGE_SMOKE, x, y, z, 0,
                            -Math.cos(ang) * 0.02, 0.05, -Math.sin(ang) * 0.02, 1.0);
                }
            }
        }
        if (fx.lime) {
            // 2. Lime: happy-villager sparkles rising + a lime swirl ring.
            force(sl, ParticleTypes.HAPPY_VILLAGER, fx.cx, fx.bottom + fx.height * 0.4, fx.cz,
                    4, fx.radius * 0.5, fx.height * 0.4, fx.radius * 0.5, 0.02);
            force(sl, LIME_DUST, fx.cx, fx.bottom + fx.height * 0.5, fx.cz,
                    8, fx.radius, fx.height * 0.5, fx.radius, 0.0);
        } else {
            // 2. Soul particles rising through the hitbox volume.
            force(sl, ParticleTypes.SOUL, fx.cx, fx.bottom + fx.height * 0.4, fx.cz,
                    4, fx.radius * 0.5, fx.height * 0.4, fx.radius * 0.5, 0.02);
            // 3. Enderman-style teleport swirl (portal particles) around the column.
            force(sl, ParticleTypes.PORTAL, fx.cx, fx.bottom + fx.height * 0.5, fx.cz,
                    8, fx.radius, fx.height * 0.5, fx.radius, 0.0);
        }
    }

    /** Send a particle with long-distance ("force") visibility to every nearby player, so a banishment is
     *  visible even when it happens far away (e.g. a beam banishing something 50-100 blocks off). */
    private static <T extends net.minecraft.core.particles.ParticleOptions> void force(
            ServerLevel sl, T type, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed) {
        for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(x, y, z) <= 160.0 * 160.0) {
                sl.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }
}
