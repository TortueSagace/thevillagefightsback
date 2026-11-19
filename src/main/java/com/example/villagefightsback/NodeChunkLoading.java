package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A village you walk away from keeps working: the chunks around its brain node stay loaded, so the node's
 * economy, its spawners and its builders keep running with no player in sight.
 *
 * <p>Village progress is gated on {@code level.isLoaded(node)} everywhere it is computed, which is exactly
 * what stops an abandoned village from advancing -- and what a player who walks 500 blocks away sees: the
 * village freezes until they come back. This module hands the node a small force-loaded island instead.
 *
 * <p>Only the node's own 3x3 chunks are held ({@link #RADIUS}), not the whole walled area: the village's
 * decisions all hang off the node, and holding a 160-block ring of chunks per village would be a real cost
 * to every server for a view nobody is looking at. Villages with a player within {@link #PLAYER_COVERS} are
 * left alone -- vanilla already keeps those loaded -- and at most {@value #MAX_VILLAGES} of the rest are
 * held at once, the ones nearest a player first, so a server full of villages does not force-load all of
 * them.
 *
 * <p>Everything taken is handed back: a chunk is released as soon as its village gets a visitor, drops out
 * of the near-player cap, loses its node, or the server stops. {@code setChunkForced} is the /forceload
 * mechanism, which persists to disk, so releasing on shutdown matters -- a held ticket that outlives the
 * server would keep that area ticking forever.
 */
public final class NodeChunkLoading {
    private NodeChunkLoading() {}

    private static final int INTERVAL = 100;           // re-evaluate every 5s
    private static final int RADIUS = 1;               // holds 3x3 chunks around the node
    private static final int MAX_VILLAGES = 8;         // ceiling on force-loaded villages per server
    private static final double PLAYER_COVERS = 96.0;  // a player this close already keeps the node ticking

    /** Chunks this module is currently holding, per level, so exactly those are released again. */
    private static final Map<ResourceKey<Level>, Set<Long>> HELD = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) return;

        List<GlobalPos> wanted = wantedVillages(server);

        Map<ResourceKey<Level>, Set<Long>> next = new HashMap<>();
        for (GlobalPos gp : wanted) {
            Set<Long> held = next.computeIfAbsent(gp.dimension(), k -> new HashSet<>());
            ChunkPos centre = new ChunkPos(gp.pos());
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    held.add(ChunkPos.asLong(centre.x + dx, centre.z + dz));
                }
            }
        }

        // Take what is new before giving anything back, so a village that merely moved in the ordering does
        // not flicker its chunks out and back in between two passes.
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : next.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) continue;
            Set<Long> before = HELD.get(e.getKey());
            for (long key : e.getValue()) {
                if (before != null && before.contains(key)) continue;
                level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), true);
            }
        }
        release(server, next);

        HELD.clear();
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : next.entrySet()) {
            HELD.put(e.getKey(), e.getValue());
        }
    }

    /** The villages that should be held this pass: node loaded-able, nobody near, nearest to a player first. */
    private static List<GlobalPos> wantedVillages(MinecraftServer server) {
        BrainNodeData data = BrainNodeData.get(server);
        List<GlobalPos> candidates = new ArrayList<>();
        for (GlobalPos gp : data.blocks()) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null) continue;
            double d = nearestPlayerDistSqr(level, gp.pos().getX(), gp.pos().getZ());
            if (d <= PLAYER_COVERS * PLAYER_COVERS) continue; // a player is there: vanilla keeps it loaded
            candidates.add(gp);
        }
        candidates.sort(Comparator.comparingDouble(gp -> {
            ServerLevel level = server.getLevel(gp.dimension());
            return level == null ? Double.MAX_VALUE
                    : nearestPlayerDistSqr(level, gp.pos().getX(), gp.pos().getZ());
        }));
        return candidates.size() > MAX_VILLAGES ? new ArrayList<>(candidates.subList(0, MAX_VILLAGES)) : candidates;
    }

    /** Squares of the horizontal distance to the closest player in {@code level}; MAX_VALUE if it has none. */
    private static double nearestPlayerDistSqr(ServerLevel level, int x, int z) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (x + 0.5), dz = player.getZ() - (z + 0.5);
            double d = dx * dx + dz * dz;
            if (d < best) best = d;
        }
        return best;
    }

    /** Release every chunk held last pass that is not in {@code keep}. */
    private static void release(MinecraftServer server, Map<ResourceKey<Level>, Set<Long>> keep) {
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : HELD.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) continue;
            Set<Long> still = keep.get(e.getKey());
            for (long key : e.getValue()) {
                if (still != null && still.contains(key)) continue;
                level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
            }
        }
    }

    /** Give every held chunk back on shutdown, so the forceload file does not outlive the villages it served. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        release(event.getServer(), Map.of());
        HELD.clear();
    }
}
