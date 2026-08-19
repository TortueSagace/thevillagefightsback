package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * Stage-4 "air strike": a scripted bombing run of 27 vertical rockets (three 3x3 waves, one second apart) onto
 * a single target. {@link BrainNodeEvents} decides WHEN to call {@link #launch} (every 10 minutes, if a village
 * ally was attacked recently) and on WHOM; this class runs the multi-second cinematic -- the two chat lines,
 * the three waves, the chunk force-load that keeps the impact column ticking, and the final survived/not verdict.
 *
 * Each rocket is a {@link RocketEntity#dropFromSky} that spawns at the world ceiling and falls dead-straight
 * down (literally from the sky -- not from a fixed height above the target, which would embed them in a cave
 * ceiling), with the exact same blast as a gigachad golem's rocket.
 */
public final class AirStrikeManager {
    private AirStrikeManager() {}

    /** Target-clearance cylinder radius, shared with {@link BrainNodeEvents}'s candidate filter. */
    static final int CLEAR_RADIUS = 10;
    // Grid cells sit ~CLEAR_RADIUS/sqrt(2) apart (~7), so the 3x3 footprint's corners land right at the clearance
    // edge while staying ~1.4x the blast radius apart -- the nine overlapping blasts blanket the zone with little
    // waste (a naive 1-block-apart grid would dump all nine into the same crater).
    private static final int GRID_SPACING = (int) Math.round(CLEAR_RADIUS / Math.sqrt(2.0));
    private static final int WAVES = 3;

    private static final int T_LINE2 = 40;     // 2s after line 1: "... and X is not gonna like it!" + wave 1
    private static final int WAVE_GAP = 20;    // 1s between waves
    private static final int VERDICT_MARGIN = 30; // grace after the last wave's rockets are due to land

    private static final class Strike {
        final ServerLevel level;
        final LivingEntity target;
        final String name;
        final int cx, cy, cz, spawnY;          // grid centre (target pos at launch) + sky spawn height
        final long start;
        final long verdictAge;                 // age at which to check survival (scales with the fall distance)
        final List<ChunkPos> forced;
        int wavesFired;

        Strike(ServerLevel level, LivingEntity target, String name, int cx, int cy, int cz, int spawnY,
               long start, long verdictAge, List<ChunkPos> forced) {
            this.level = level; this.target = target; this.name = name;
            this.cx = cx; this.cy = cy; this.cz = cz; this.spawnY = spawnY;
            this.start = start; this.verdictAge = verdictAge; this.forced = forced;
        }
    }

    private static final List<Strike> ACTIVE = new ArrayList<>();

    /** Begin a strike centred on {@code target}'s current position: force-load the impact column's chunks and
     *  post the first chat line; the rest unfolds tick-by-tick in {@link #onServerTick}. */
    public static void launch(ServerLevel level, LivingEntity target) {
        int cx = (int) Math.floor(target.getX());
        int cy = (int) Math.floor(target.getY());
        int cz = (int) Math.floor(target.getZ());
        int spawnY = level.getMaxBuildHeight() - 1;                 // literally the top of the sky

        // Force-load every chunk the 3x3 footprint touches so the rockets fall + detonate even with no player
        // nearby. Record only chunks WE newly forced, so cleanup never releases a player's own forced chunk.
        List<ChunkPos> forced = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int gx = -1; gx <= 1; gx++) {
            for (int gz = -1; gz <= 1; gz++) {
                ChunkPos cp = new ChunkPos((cx + gx * GRID_SPACING) >> 4, (cz + gz * GRID_SPACING) >> 4);
                if (!seen.add(cp.toLong())) continue;
                if (!level.getForcedChunks().contains(cp.toLong())) {
                    level.setChunkForced(cp.x, cp.z, true);
                    forced.add(cp);
                }
            }
        }

        // The last wave fires at T_LINE2 + (WAVES-1)*WAVE_GAP; its rockets fall (spawnY - cy) blocks at SPEED.
        long lastWaveAge = T_LINE2 + (long) (WAVES - 1) * WAVE_GAP;
        long fallTicks = (long) Math.ceil((spawnY - cy) / RocketEntity.SPEED);
        long verdictAge = lastWaveAge + fallTicks + VERDICT_MARGIN;

        ACTIVE.add(new Strike(level, target, target.getDisplayName().getString(),
                cx, cy, cz, spawnY, level.getGameTime(), verdictAge, forced));
        announce(level, cx + 0.5, cy + 0.5, cz + 0.5, "§fSomething is falling from the sky...");
        // Record the strike in the nearest brain node's reflex log (so it shows in the Logs tab).
        BrainReflexes.logExternal(level, target.blockPosition(),
                "Air strike on " + target.getDisplayName().getString());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Strike> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            long age = s.level.getGameTime() - s.start;
            if (age == T_LINE2) {
                announce(s.level, s.cx + 0.5, s.cy + 0.5, s.cz + 0.5, "§f... and " + s.name + " is not gonna like it!");
                fireWave(s);
            } else if (s.wavesFired > 0 && s.wavesFired < WAVES && age == T_LINE2 + (long) s.wavesFired * WAVE_GAP) {
                fireWave(s);
            } else if (age >= s.verdictAge) {
                boolean survived = s.target.isAlive() && !s.target.isRemoved();
                announce(s.level, s.cx + 0.5, s.cy + 0.5, s.cz + 0.5, survived
                        ? "§e" + s.name + " somehow survived the air strike."
                        : "§a" + s.name + " did not survive the air strike.");
                for (ChunkPos cp : s.forced) s.level.setChunkForced(cp.x, cp.z, false);
                it.remove();
            }
        }
    }

    /** Free the chunks of any strike still in flight when the server stops, so a quit/shutdown mid-strike never
     *  orphans a force-loaded chunk (setChunkForced persists to disk -- an orphan would keep that area, and any
     *  village in it, ticking forever). Completed strikes already released their chunks in {@link #onServerTick}. */
    @SubscribeEvent
    public static void onServerStopping(com.example.villagefightsback.platform.event.ServerStoppingEvent event) {
        for (Strike s : ACTIVE) {
            if (s.level == null) continue;
            for (ChunkPos cp : s.forced) s.level.setChunkForced(cp.x, cp.z, false);
        }
        ACTIVE.clear();
    }

    /** One 3x3 wave: nine rockets dropped from the sky, GRID_SPACING apart, centred on the target's launch spot. */
    private static void fireWave(Strike s) {
        for (int gx = -1; gx <= 1; gx++) {
            for (int gz = -1; gz <= 1; gz++) {
                double x = s.cx + 0.5 + gx * GRID_SPACING;
                double z = s.cz + 0.5 + gz * GRID_SPACING;
                RocketEntity.dropFromSky(s.level, x, s.spawnY, z);
            }
        }
        s.wavesFired++;
    }

    /** Announce a strike line ONLY to players near the impact, so a stage-4 village's strike never appears to a
     *  player standing at an unrelated (e.g. stage-3) village far away. */
    private static final double ANNOUNCE_RADIUS = 256.0;

    private static void announce(ServerLevel level, double x, double y, double z, String msg) {
        Component c = Component.literal(msg);
        double r2 = ANNOUNCE_RADIUS * ANNOUNCE_RADIUS;
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) <= r2) p.sendSystemMessage(c);
        }
    }
}
