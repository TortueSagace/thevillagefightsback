package com.example.villagefightsback.wall;

import java.util.HashMap;
import java.util.Map;

import com.example.villagefightsback.BrainNodeData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Live coordinator for wall building (analogous to {@code FlareRally}): finds the wall a builder should
 * join, and holds short-lived per-block reservations so two villagers never target the same block. The
 * authoritative plans live in {@link BrainNodeData}; reservations here are transient (never persisted).
 */
public final class WallBuild {
    private WallBuild() {}

    public static final long RESERVE_TTL = 100L;        // ticks a target stays reserved without refresh

    // brainNode -> (target block pos as long -> reservation expiry game time)
    private static final Map<GlobalPos, Map<Long, Long>> RESERVATIONS = new HashMap<>();
    // brainNode -> (parked block pos as long -> retry-after game time): a cell no builder could reach/finish,
    // hidden from ALL builders for a while so they don't churn on it (shared, unlike a per-builder skip).
    private static final Map<GlobalPos, Map<Long, Long>> SKIPS = new HashMap<>();
    // brainNode -> (block pos as long -> how many times a builder has abandoned it). Cells the builders keep
    // failing to reach are eventually sealed directly by the brain node so the wall can't be left with holes.
    private static final Map<GlobalPos, Map<Long, Integer>> ABANDONS = new HashMap<>();

    /**
     * Nearest incomplete wall plan a builder at {@code near} belongs to, or null. The join radius is the
     * wall's OWN extent (+margin), not a fixed distance from the village centre -- otherwise a builder out on
     * a far wall section of a big village would "lose" its plan and stop building (and then wander off /
     * settle).
     */
    public static WallPlan planFor(ServerLevel level, BlockPos near) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        WallPlan best = null;
        double bestD = Double.MAX_VALUE;
        // Consider BOTH the inner (cobblestone) and the stage-3 outer (obsidian) walls. The inner wall is
        // complete (and skipped) by the time an outer wall is being built, so builders flow onto the outer one.
        for (WallPlan p : data.wallPlans()) {
            if (p.isComplete() || !p.brainNode().dimension().equals(level.dimension())) continue;
            double d = p.distSqrTo(near);
            if (d < bestD && d <= p.joinRadiusSqr()) { bestD = d; best = p; }
        }
        for (WallPlan p : data.outerWallPlans()) {
            if (p.isComplete() || !p.brainNode().dimension().equals(level.dimension())) continue;
            double d = p.distSqrTo(near);
            if (d < bestD && d <= p.joinRadiusSqr()) { bestD = d; best = p; }
        }
        return best;
    }

    /** Try to claim a target. Returns false if another builder already holds an unexpired reservation. */
    public static boolean reserve(GlobalPos plan, BlockPos pos, long now) {
        Map<Long, Long> map = RESERVATIONS.computeIfAbsent(plan, k -> new HashMap<>());
        long key = pos.asLong();
        Long exp = map.get(key);
        if (exp != null && exp > now) return false;
        map.put(key, now + RESERVE_TTL);
        return true;
    }

    /** Refresh the reservation a builder already holds so it doesn't expire mid-build. */
    public static void refresh(GlobalPos plan, BlockPos pos, long now) {
        Map<Long, Long> map = RESERVATIONS.get(plan);
        if (map != null) map.put(pos.asLong(), now + RESERVE_TTL);
    }

    public static boolean isReserved(GlobalPos plan, BlockPos pos, long now) {
        Map<Long, Long> map = RESERVATIONS.get(plan);
        if (map == null) return false;
        Long exp = map.get(pos.asLong());
        return exp != null && exp > now;
    }

    public static void release(GlobalPos plan, BlockPos pos) {
        Map<Long, Long> map = RESERVATIONS.get(plan);
        if (map != null) map.remove(pos.asLong());
    }

    /** Park a cell no builder could reach/finish, hidden from every builder until {@code until}. */
    public static void skip(GlobalPos plan, BlockPos pos, long until) {
        SKIPS.computeIfAbsent(plan, k -> new HashMap<>()).put(pos.asLong(), until);
    }

    /** Record that a builder gave up on this cell; returns the new running abandon count for it. */
    public static int recordAbandon(GlobalPos plan, BlockPos pos) {
        return ABANDONS.computeIfAbsent(plan, k -> new HashMap<>()).merge(pos.asLong(), 1, Integer::sum);
    }

    /** Snapshot of (cell pos as long -> abandon count) for a plan; empty if none. */
    public static Map<Long, Integer> abandons(GlobalPos plan) {
        return ABANDONS.getOrDefault(plan, java.util.Collections.emptyMap());
    }

    /** How many times builders have given up on this cell (0 if never). */
    public static int abandonCount(GlobalPos plan, BlockPos pos) {
        Map<Long, Integer> m = ABANDONS.get(plan);
        if (m == null) return 0;
        Integer v = m.get(pos.asLong());
        return v == null ? 0 : v;
    }

    /** The wall plan whose footprint (horizontally) ENCLOSES {@code pos}, or null. Used to leash defenders to
     *  the walled zone (complete or not -- a half-built wall still defines the village's footprint). */
    public static WallPlan enclosingPlan(ServerLevel level, BlockPos pos) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        for (WallPlan p : data.wallPlans()) {
            if (!p.brainNode().dimension().equals(level.dimension())) continue;
            if (p.contains(pos)) return p;
        }
        return null;
    }

    /** Forget a cell's abandon count + skip (e.g. the brain node just sealed it, or it got built). */
    public static void clearAbandon(GlobalPos plan, BlockPos pos) {
        Map<Long, Integer> m = ABANDONS.get(plan);
        if (m != null) m.remove(pos.asLong());
        Map<Long, Long> s = SKIPS.get(plan);
        if (s != null) s.remove(pos.asLong());
    }

    public static boolean isSkipped(GlobalPos plan, BlockPos pos, long now) {
        Map<Long, Long> map = SKIPS.get(plan);
        if (map == null) return false;
        Long until = map.get(pos.asLong());
        return until != null && until > now;
    }

    /** Drop all reservations/skips for a plan (call when the wall completes or the brain node is removed). */
    public static void clearPlan(GlobalPos plan) {
        RESERVATIONS.remove(plan);
        SKIPS.remove(plan);
        ABANDONS.remove(plan);
    }
}
