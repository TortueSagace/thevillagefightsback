package com.example.villagergunners;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Generates the Limbo's floating bedrock cubes on demand. When a Limbo chunk loads we queue it; on the next
 * server tick (main thread) we build any cube whose origin is that chunk, exactly once ({@link LimboCubeData}).
 * Chunks load only when a player is near, so -- apart from the always-loaded central cube -- the Limbo costs
 * nothing while empty.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboWorldgenEvents {
    private LimboWorldgenEvents() {}

    private static final ConcurrentLinkedQueue<Long> PENDING = new ConcurrentLinkedQueue<>();
    private static final int CHECKS_PER_TICK = 64;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!LimboDimension.LIMBO.equals(sl.dimension())) return;
        PENDING.add(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null) return;

        long _p = ModProfiler.now();
        LimboCubeData data = LimboCubeData.get(limbo);
        long seed = limbo.getSeed();
        for (int i = 0; i < CHECKS_PER_TICK; i++) {
            Long key = PENDING.poll();
            if (key == null) break;
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            LimboCubes.Cube cube = LimboCubes.cubeForChunk(seed, cx, cz);
            if (cube == null) continue;                  // no cube originates here
            if (!limbo.hasChunk(cx, cz)) continue;       // unloaded before we got to it -> retry on next load
            if (data.markBuilt(key)) {
                LimboCubes.build(limbo, cube);           // first time only
            }
        }
        ModProfiler.add("limbo.cubes", _p);
    }
}
