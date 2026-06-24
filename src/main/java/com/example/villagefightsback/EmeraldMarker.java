package com.example.villagefightsback;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The EMERALD MARKER rally beacon (1.1.2). The brain node paints a distant target with it; every emerald unit
 * within 100 blocks then runs at the nearest marked target ({@code RallyToMarkedGoal}). It is used SPARINGLY --
 * at most {@link #MAX_ACTIVE} marks may be live at once -- so the swarm converges, instead of scattering across
 * dozens of marks.
 *
 * <p>Backed by a tiny {@link WeakHashMap} of entity -> expiry game-time so each unit's rally check is O(few),
 * not a 100-block entity scan. The {@link ModEffects#EMERALD_MARKER} effect (applied with visible particles) is
 * the player-facing half; this registry is the AI half and drives expiry.
 */
public final class EmeraldMarker {
    private EmeraldMarker() {}

    /** Don't overuse: at most this many targets may carry the marker at once. */
    public static final int MAX_ACTIVE = 2;
    public static final double RALLY_RANGE = 100.0;
    private static final int DURATION = 20 * 30; // 30s

    private static final Map<LivingEntity, Long> MARKED = new WeakHashMap<>(); // entity -> expiry game-time

    /** Mark {@code target}: rally beacon + the visible green effect + a vanilla Glowing outline. */
    public static void mark(ServerLevel level, LivingEntity target) {
        MARKED.put(target, level.getGameTime() + DURATION);
        // visible=true on purpose, so the player sees the marked mob (unlike the mod's other, hidden effects).
        target.addEffect(new MobEffectInstance(ModEffects.EMERALD_MARKER.get(), DURATION, 0, false, true, true));
        // Also Glowing: a through-walls outline so the player can track the scented target. (request 1.1.4)
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, DURATION, 0, false, true, true));
    }

    public static boolean isMarked(LivingEntity t) {
        Long exp = MARKED.get(t);
        return exp != null && t.isAlive() && t.level().getGameTime() <= exp;
    }

    /** Nearest live marked target to {@code from} within {@code range} in the same level (null if none). Self-prunes. */
    public static LivingEntity nearest(LivingEntity from, double range) {
        long now = from.level().getGameTime();
        double best = range * range;
        LivingEntity result = null;
        Iterator<Map.Entry<LivingEntity, Long>> it = MARKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> e = it.next();
            LivingEntity t = e.getKey();
            if (t == null || !t.isAlive() || now > e.getValue()) { it.remove(); continue; }
            if (t.level() != from.level()) continue;
            double d = t.distanceToSqr(from);
            if (d <= best) { best = d; result = t; }
        }
        return result;
    }

    /** How many marks are currently live in {@code level} (prunes stale entries as it counts). */
    public static int activeCount(ServerLevel level) {
        long now = level.getGameTime();
        int n = 0;
        Iterator<Map.Entry<LivingEntity, Long>> it = MARKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> e = it.next();
            LivingEntity t = e.getKey();
            if (t == null || !t.isAlive() || now > e.getValue()) { it.remove(); continue; }
            if (t.level() == level) n++;
        }
        return n;
    }
}
