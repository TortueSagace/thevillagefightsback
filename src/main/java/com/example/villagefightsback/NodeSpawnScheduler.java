package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Staggers the Barracks Node's guard spawns: the requested guards appear one per second, so they don't all
 *  pop in on the same tick (see {@link BarracksNodeBlock}). */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BATCHES.isEmpty()) return;
        Iterator<Batch> it = BATCHES.iterator();
        while (it.hasNext()) {
            Batch b = it.next();
            if (b.level.getGameTime() < b.nextTick) continue;
            NodeSpawns.spawnGuard(b.level, b.node, b.stage);
            b.remaining--;
            b.nextTick = b.level.getGameTime() + SPACING;
            if (b.remaining <= 0) it.remove();
        }
    }
}
