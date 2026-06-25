package com.example.villagefightsback;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Drives the dimensional drift: when a drift Limbo chunk loads it is linked to its twin (overworld/nether)
 * chunk and queued for a one-time block+biome copy, amortized at a fixed per-tick section budget. Player
 * break/place in either a Limbo drift chunk or a twin chunk is mirrored to the other side (single block). A
 * slow periodic re-copy catches environment changes. Living entities never cross.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LimboDriftEvents {
    private LimboDriftEvents() {}

    private record Link(LimboDrift.Type type, ResourceKey<Level> twinDim, ChunkPos twinChunk) {}

    private static final Map<Long, Link> LINKS = new ConcurrentHashMap<>();      // limbo chunk -> twin link
    private static final Map<String, Set<Long>> REVERSE = new ConcurrentHashMap<>(); // twin chunk -> limbo chunks
    private static final ArrayDeque<long[]> QUEUE = new ArrayDeque<>();          // {limboKey, sectionCursor}
    private static final Set<Long> QUEUED = ConcurrentHashMap.newKeySet();

    private static final int BUDGET = 4;             // sections copied per tick (16,384 blocks max)
    private static final int SECTIONS = 24;          // 384-high Limbo / 16

    private static String twinKey(ResourceKey<Level> dim, ChunkPos cp) {
        return dim.location() + "|" + cp.toLong();
    }

    // --- linking on load / unload -------------------------------------------------------------
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !LimboDimension.LIMBO.equals(sl.dimension())) return;
        ChunkPos cp = event.getChunk().getPos();
        LimboDrift.Type t = LimboDrift.type(sl.getSeed(), cp.x, cp.z);
        if (t == LimboDrift.Type.NONE) return;
        ChunkPos twin = LimboDrift.twinChunk(t, cp.x, cp.z);
        ResourceKey<Level> twinDim = LimboDrift.twinDimension(t);
        long key = cp.toLong();
        LINKS.put(key, new Link(t, twinDim, twin));
        REVERSE.computeIfAbsent(twinKey(twinDim, twin), k -> ConcurrentHashMap.newKeySet()).add(key);
        enqueue(key);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !LimboDimension.LIMBO.equals(sl.dimension())) return;
        long key = event.getChunk().getPos().toLong();
        Link link = LINKS.remove(key);
        if (link != null) {
            String tk = twinKey(link.twinDim, link.twinChunk);
            Set<Long> set = REVERSE.get(tk);
            if (set != null) { set.remove(key); if (set.isEmpty()) REVERSE.remove(tk); }
        }
        QUEUED.remove(key);
    }

    private static void enqueue(long key) {
        if (QUEUED.add(key)) QUEUE.add(new long[]{key, 0});
    }

    // --- the bounded copy ---------------------------------------------------------------------
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null) return;

        // NOTE: no periodic twin->limbo re-copy. Re-copying + resending overwrote player edits (a broken block
        // would reappear / go invisible-but-solid). Player break/place still crosses both ways via the event
        // mirror below; only the one-time activation copy fills a chunk.
        if (QUEUE.isEmpty()) return;

        long _p = ModProfiler.now();
        int budget = BUDGET;
        int guard = QUEUE.size() + BUDGET + 8;
        while (budget > 0 && !QUEUE.isEmpty() && guard-- > 0) {
            long[] job = QUEUE.poll();
            long key = job[0];
            Link link = LINKS.get(key);
            ChunkPos lcp = new ChunkPos(key);
            if (link == null || !limbo.hasChunk(lcp.x, lcp.z)) { QUEUED.remove(key); continue; }
            ServerLevel twin = server.getLevel(link.twinDim);
            if (twin == null) { QUEUED.remove(key); continue; }

            LevelChunk lc = limbo.getChunk(lcp.x, lcp.z);
            LevelChunk tc = twin.getChunk(link.twinChunk.x, link.twinChunk.z); // loads/generates the twin
            if (job[1] == 0) displaceEntities(limbo, lcp); // move any units out before the terrain overwrites them
            LimboDrift.copySection(limbo, lc, tc, link.type, (int) job[1]);
            budget--;
            int next = (int) job[1] + 1;
            if (next < SECTIONS) {
                QUEUE.add(new long[]{key, next});
            } else {
                LimboDrift.copyBlockEntities(limbo, lc, tc);
                LimboDrift.resend(limbo, lc);
                QUEUED.remove(key);
            }
        }
        ModProfiler.add("limbo.drift", _p);
    }

    // --- two-way single-block mirror (player break / place) -----------------------------------
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        mirror(event.getLevel(), event.getPos(), Blocks.AIR.defaultBlockState());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        mirror(event.getLevel(), event.getPos(), event.getPlacedBlock());
    }

    /** Mirror a single block change to the linked counterpart (no event re-fires from setBlock, so no loop). */
    private static void mirror(LevelAccessor la, BlockPos pos, BlockState state) {
        if (!(la instanceof ServerLevel sl)) return;
        MinecraftServer server = sl.getServer();
        if (server == null) return;
        ChunkPos cp = new ChunkPos(pos);

        if (LimboDimension.LIMBO.equals(sl.dimension())) {
            Link link = LINKS.get(cp.toLong());
            if (link == null) return;
            ServerLevel twin = server.getLevel(link.twinDim);
            if (twin == null) return;
            BlockPos tp = LimboDrift.toTwinBlock(link.twinChunk, pos);
            if (!twin.isOutsideBuildHeight(tp)) twin.setBlock(tp, state, 3);
        } else {
            Set<Long> limboKeys = REVERSE.get(twinKey(sl.dimension(), cp));
            if (limboKeys == null || limboKeys.isEmpty()) return;
            ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
            if (limbo == null) return;
            for (Long key : limboKeys) {
                BlockPos lp = LimboDrift.toLimboBlock(new ChunkPos(key), pos);
                if (!limbo.isOutsideBuildHeight(lp)) limbo.setBlock(lp, state, 3);
            }
        }
    }

    // --- displace units out of a drift chunk before its terrain materialises -------------------------
    /** When a drift chunk's copy starts, shove any non-player entities standing in it into the nearest NON-drift
     *  neighbour chunk (never destroying them) and drop a fake-bedrock pad under each so they don't fall into the
     *  void. Runs once per chunk (at section cursor 0). */
    private static void displaceEntities(ServerLevel limbo, ChunkPos cp) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                cp.getMinBlockX(), limbo.getMinBuildHeight(), cp.getMinBlockZ(),
                cp.getMaxBlockX() + 1, limbo.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
        java.util.List<net.minecraft.world.entity.Entity> units = limbo.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class, box,
                e -> !(e instanceof net.minecraft.world.entity.player.Player));
        if (units.isEmpty()) return;
        long seed = limbo.getSeed();
        // Prefer a cardinal non-drift neighbour, then a diagonal; if every neighbour is itself drift, just push +X.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int sx = 1, sz = 0;
        for (int[] d : dirs) {
            if (LimboDrift.type(seed, cp.x + d[0], cp.z + d[1]) == LimboDrift.Type.NONE) { sx = d[0]; sz = d[1]; break; }
        }
        for (net.minecraft.world.entity.Entity e : units) {
            double nx = e.getX() + sx * 16.0, nz = e.getZ() + sz * 16.0;
            LimboDimension.ensureFloorUnder(limbo, BlockPos.containing(nx, e.getY(), nz));
            e.teleportTo(nx, e.getY(), nz);
        }
    }
}
