package com.example.villagefightsback;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Generates the Limbo's floating bedrock cubes on demand. When a Limbo chunk loads we queue it; on the next
 * server tick (main thread) we build any cube whose origin is that chunk, exactly once ({@link LimboCubeData}).
 * Chunks load only when a player is near, so -- apart from the always-loaded central cube -- the Limbo costs
 * nothing while empty.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
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
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
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
            if (!limbo.hasChunk(cx, cz)) continue;       // unloaded before we got to it -> retry on next load
            migrateBedrock(limbo, cx, cz);               // convert any leftover REAL bedrock to fake bedrock
            LimboCubes.Cube cube = LimboCubes.cubeForChunk(seed, cx, cz);
            if (cube == null) continue;                  // no cube originates here
            if (data.markBuilt(key)) {
                LimboCubes.build(limbo, cube);           // first time only
            }
        }
        ModProfiler.add("limbo.cubes", _p);
    }

    /** One-time conversion of any pre-existing REAL bedrock in a Limbo chunk to fake bedrock (so old worlds get
     *  the pass-through cubes too). Palette-gated: void chunks and already-converted chunks cost almost nothing. */
    private static void migrateBedrock(ServerLevel limbo, int cx, int cz) {
        net.minecraft.world.level.chunk.LevelChunk chunk = limbo.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return;
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> isBedrock =
                s -> s.is(net.minecraft.world.level.block.Blocks.BEDROCK);
        net.minecraft.world.level.block.state.BlockState fake = ModBlocks.FAKE_BEDROCK.get().defaultBlockState();
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        for (int idx = 0; idx < sections.length; idx++) {
            net.minecraft.world.level.chunk.LevelChunkSection sec = sections[idx];
            if (sec.hasOnlyAir() || !sec.maybeHas(isBedrock)) continue; // skip empty / bedrock-free sections
            int y0 = limbo.getMinBuildHeight() + (idx << 4);
            for (int dy = 0; dy < 16; dy++) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        if (sec.getBlockState(dx, dy, dz).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                            limbo.setBlock(m.set((cx << 4) + dx, y0 + dy, (cz << 4) + dz), fake, 2);
                        }
                    }
                }
            }
        }
    }
}
