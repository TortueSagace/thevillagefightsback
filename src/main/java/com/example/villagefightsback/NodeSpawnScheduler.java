package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Staggers the Barracks Node's guard spawns: the requested guards appear one per second, so they don't all
 *  pop in on the same tick (see {@link BarracksNodeBlock}). */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NodeSpawnScheduler {
    private NodeSpawnScheduler() {}

    private static final int SPACING = 20; // 1 second between guards

    private static final class Batch {
        final ServerLevel level;
        final BlockPos node;
        final int stage;
        int remaining;
        long nextTick;
        Batch(ServerLevel level, BlockPos node, int stage, int count, long firstTick) {
            this.level = level; this.node = node; this.stage = stage; this.remaining = count; this.nextTick = firstTick;
        }
    }
    private static final List<Batch> BATCHES = new ArrayList<>();

    /** Queue {@code count} guards at {@code node}: the first immediately, then one per second. */
    public static void scheduleGuards(ServerLevel level, BlockPos node, int stage, int count) {
        if (count > 0) BATCHES.add(new Batch(level, node, stage, count, level.getGameTime()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BATCHES.isEmpty()) return;
        Iterator<Batch> it = BATCHES.iterator();
        while (it.hasNext()) {
            Batch b = it.next();
            if (b.level.getGameTime() < b.nextTick) continue;
            if (b.level.isLoaded(b.node)) {
                if (b.level.getBlockState(b.node).getBlock() instanceof AbstractNodeBlock) {
                    try { NodeSpawns.spawnGuard(b.level, b.node, b.stage); }
                    catch (Throwable t) { TheVillageFightsBack.LOGGER.error("barracks guard spawn failed at {}", b.node, t); }
                } else {
                    it.remove();   // the barracks node was mined/replaced mid-batch -> stop the in-flight guards
                    continue;
                }
            }
            // Whether we spawned (loaded + present) or skipped (chunk unloaded), advance so the batch always
            // terminates -- an unloaded barracks just loses its remaining guards rather than queueing forever.
            b.remaining--;
            b.nextTick = b.level.getGameTime() + SPACING;
            if (b.remaining <= 0) it.remove();
        }
    }

    /** Clear the queue on world unload so no stale Batch/ServerLevel survives into the next (integrated) world. */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        BATCHES.clear();
    }
}
