package com.example.villagergunners;

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
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboBanishFx {
    private LimboBanishFx() {}

    private static final int DURATION = 60;   // ~3 seconds
    private static final int ARMS = 3;        // spiral arms
    private static final int HEIGHT_STEPS = 8;
    private static final int MAX_ACTIVE = 16; // bound the particle load when many are banished at once

    private record Fx(ServerLevel level, double cx, double bottom, double cz, double radius, double height, long end) {}
    private static final List<Fx> ACTIVE = new ArrayList<>();

    /** Begin the swirl where {@code e}'s hitbox currently is. Call just BEFORE the creature is teleported away. */
    public static void start(Entity e) {
        if (!(e.level() instanceof ServerLevel sl) || ACTIVE.size() >= MAX_ACTIVE) return;
        AABB b = e.getBoundingBox();
        double radius = Math.max(0.6, Math.max(b.getXsize(), b.getZsize()) * 0.65);
        double height = Math.max(1.2, b.getYsize());
        ACTIVE.add(new Fx(sl, (b.minX + b.maxX) / 2.0, b.minY, (b.minZ + b.maxZ) / 2.0,
                radius, height, sl.getGameTime() + DURATION));
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
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 0,
                        -Math.cos(ang) * 0.02, 0.05, -Math.sin(ang) * 0.02, 1.0);
            }
        }
        // 2. Soul particles rising through the hitbox volume.
        sl.sendParticles(ParticleTypes.SOUL, fx.cx, fx.bottom + fx.height * 0.4, fx.cz,
                4, fx.radius * 0.5, fx.height * 0.4, fx.radius * 0.5, 0.02);
        // 3. Enderman-style teleport swirl (portal particles) around the column.
        sl.sendParticles(ParticleTypes.PORTAL, fx.cx, fx.bottom + fx.height * 0.5, fx.cz,
                8, fx.radius, fx.height * 0.5, fx.radius, 0.0);
    }
}
