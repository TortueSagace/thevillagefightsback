package com.example.villagergunners.wall;

import java.util.HashMap;
import java.util.Map;

import com.example.villagergunners.GravemindData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Live coordinator for wall building (analogous to {@code FlareRally}): finds the wall a builder should
 * join, and holds short-lived per-block reservations so two villagers never target the same block. The
 * authoritative plans live in {@link GravemindData}; reservations here are transient (never persisted).
 */
public final class WallBuild {
    private WallBuild() {}

    public static final long RESERVE_TTL = 100L;        // ticks a target stays reserved without refresh
    private static final double JOIN_RADIUS = 96.0;     // villagers within this of a village join its wall
    private static final double JOIN_RADIUS_SQR = JOIN_RADIUS * JOIN_RADIUS;

    // gravemind -> (target block pos as long -> reservation expiry game time)
    private static final Map<GlobalPos, Map<Long, Long>> RESERVATIONS = new HashMap<>();

    /** Nearest incomplete wall plan to {@code near} in the same dimension, within the join radius, or null. */
    public static WallPlan planFor(ServerLevel level, BlockPos near) {
        GravemindData data = GravemindData.get(level.getServer());
        WallPlan best = null;
        double bestD = Double.MAX_VALUE;
        for (WallPlan p : data.wallPlans()) {
            if (p.isComplete() || !p.gravemind().dimension().equals(level.dimension())) continue;
            double d = p.distSqrTo(near);
            if (d < bestD && d <= JOIN_RADIUS_SQR) { bestD = d; best = p; }
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
}
