package com.example.villagefightsback;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * CHUNK PORTALS (work in progress, v1.2.1) -- server side. Once {@link Config#chunkPortalsEnabled()} is on AND
 * a brain node has reached stage 3 while in reach, ~1% of overworld chunks are turned into a box of four
 * vertical portal walls that lead 1:1 into the Limbo (NO 16x compression). The chunk the brain sits in, plus
 * the 5x5 around it, is never transformable; chunks whose Limbo twin is a {@link LimboDrift} chunk are also
 * skipped, so every portal's Limbo footprint is empty void or a deterministic {@link LimboCubes} cube. Every
 * 10 minutes each loaded portal emits one random Limbo-raid creature just outside a random wall.
 *
 * <p>The whole feature is gated by the config switch plus the persisted {@code activated} flag in
 * {@link ChunkPortalData}, so nothing here runs by default. NOTE (skeleton): the walls currently render as the
 * plain green swirl; the live see-through Limbo view is a later phase. Mob cross-IN by walking a wall is also a
 * later phase (mobs only come out via the emergence below); only players walk through.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ChunkPortalManager {
    private ChunkPortalManager() {}

    /** Fraction of overworld chunks that become a chunk portal (deterministic per chunk). */
    private static final double CHANCE = 0.01;
    /** Distinct salt so portal selection never correlates with the cube/drift rolls. */
    private static final long SALT = 0x9A7C0FFEE5EED17L;
    private static final int WALL_HEIGHT = 8;            // portal panes stacked above each perimeter column
    private static final int EXCLUDE_RADIUS = 2;         // 5x5 (Chebyshev <= 2) protected zone around every brain
    private static final long EMERGE_INTERVAL = 12000L;  // one creature per portal per 10 minutes
    private static final int CHECK_BUDGET = 256;         // queued chunks examined per manage() pass
    private static final int BUILD_BUDGET = 4;           // portals actually built per manage() pass (amortised)

    /** Overworld chunks queued for a portal-build, discovered on chunk load (drained on the server thread). */
    private static final ConcurrentLinkedQueue<Long> PENDING = new ConcurrentLinkedQueue<>();
    /** Transient session state: have we swept already-loaded chunks since activation / the last off->on toggle? */
    private static boolean swept = false;
    private static boolean lastEnabled = false;

    // --- deterministic 1% selection + exclusions --------------------------------------------------------

    /** Whether overworld chunk (cx,cz) is a chunk-portal candidate (deterministic from the world seed). */
    private static boolean isChunkPortal(long seed, int cx, int cz) {
        long h = seed ^ SALT ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 27); h *= 0x94D049BB133111EBL; h ^= (h >>> 31);
        double r = (h >>> 11) * 0x1.0p-53; // [0,1)
        return r < CHANCE;
    }

    /** A candidate is eligible only if it rolls a portal, its Limbo twin is plain void (not a drift chunk), and
     *  it is outside the 5x5 protected zone of EVERY brain node. */
    private static boolean eligible(ServerLevel overworld, BrainNodeData brains, int cx, int cz) {
        long seed = overworld.getSeed();
        if (!isChunkPortal(seed, cx, cz)) return false;
        if (LimboDrift.type(seed, cx, cz) != LimboDrift.Type.NONE) return false; // skip drift-twin footprints
        return !excludedByBrain(brains, cx, cz);
    }

    /** True if (cx,cz) is within the 5x5 protected zone of any overworld brain node (stage-independent). */
    private static boolean excludedByBrain(BrainNodeData brains, int cx, int cz) {
        for (GlobalPos gp : brains.blocks()) {
            if (!gp.dimension().equals(Level.OVERWORLD)) continue;
            int bx = gp.pos().getX() >> 4, bz = gp.pos().getZ() >> 4;
            if (Math.abs(bx - cx) <= EXCLUDE_RADIUS && Math.abs(bz - cz) <= EXCLUDE_RADIUS) return true;
        }
        return false;
    }

    // --- lazy discovery: every overworld chunk load is a build candidate --------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!Config.chunkPortalsEnabled()) return; // cheap, thread-safe read; full checks happen on the tick drain
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!Level.OVERWORLD.equals(sl.dimension())) return;
        PENDING.add(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        swept = false;
        lastEnabled = false;
    }

    // --- driven from BrainNodeEvents.onServerTick (every 2s) -------------------------------------------

    /** No-op unless the config is on. Activates the feature when a stage-3+ brain is in reach, applies it to
     *  already-loaded chunks once, then drains the build queue and ticks per-portal emergence. */
    public static void manage(MinecraftServer server, BrainNodeData brains) {
        boolean enabled = Config.chunkPortalsEnabled();
        if (enabled && !lastEnabled) swept = false; // a runtime off->on toggle re-sweeps loaded chunks
        lastEnabled = enabled;
        if (!enabled) { PENDING.clear(); return; }

        ServerLevel overworld = server.overworld();
        ChunkPortalData data = ChunkPortalData.get(server);

        // Activation: the first time a stage-3+ brain is loaded, switch the 1% transformation on for good.
        if (!data.isActivated() && anyStageThreeInReach(overworld, brains)) {
            data.setActivated(true);
        }
        if (!data.isActivated()) return;

        // Immediately apply to chunks that are already loaded around the players (do it once a player exists).
        if (!swept && !overworld.players().isEmpty()) {
            sweepLoadedChunks(overworld);
            swept = true;
        }

        drainPending(overworld, brains, data);
        emerge(overworld, data);
    }

    private static boolean anyStageThreeInReach(ServerLevel overworld, BrainNodeData brains) {
        for (GlobalPos gp : brains.blocks()) {
            if (!gp.dimension().equals(Level.OVERWORLD)) continue;
            if (!overworld.isLoaded(gp.pos())) continue;       // "at reach" == the brain's chunk is loaded
            if (brains.econ(gp).getLevel() >= 3) return true;
        }
        return false;
    }

    /** Queue every chunk currently loaded around each overworld player (the "immediately apply" sweep). */
    private static void sweepLoadedChunks(ServerLevel overworld) {
        int vd = overworld.getServer().getPlayerList().getViewDistance() + 1;
        for (ServerPlayer p : overworld.players()) {
            ChunkPos c = p.chunkPosition();
            for (int dx = -vd; dx <= vd; dx++) {
                for (int dz = -vd; dz <= vd; dz++) {
                    PENDING.add(ChunkPos.asLong(c.x + dx, c.z + dz));
                }
            }
        }
    }

    private static void drainPending(ServerLevel overworld, BrainNodeData brains, ChunkPortalData data) {
        int checks = CHECK_BUDGET, built = 0;
        Long key;
        while (checks-- > 0 && built < BUILD_BUDGET && (key = PENDING.poll()) != null) {
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (data.isPortal(key)) continue;
            if (!overworld.hasChunk(cx, cz)) continue;           // unloaded before we got to it -> retry on reload
            if (!eligible(overworld, brains, cx, cz)) continue;
            buildOverworldRing(overworld, cx, cz);
            data.addPortal(key);
            data.nextSpawn().put(key, overworld.getGameTime()
                    + (long) (overworld.random.nextDouble() * EMERGE_INTERVAL)); // random phase so they desync
            built++;
        }
    }

    // --- building the four walls ----------------------------------------------------------------------

    /** Build the overworld 4-wall box around chunk (cx,cz): panes rise above the surface, only into replaceable
     *  cells so the build is non-destructive. North/south edges run along X (AXIS=X), east/west along Z. */
    private static void buildOverworldRing(ServerLevel level, int cx, int cz) {
        int minX = cx << 4, maxX = minX + 15, minZ = cz << 4, maxZ = minZ + 15;
        for (int x = minX; x <= maxX; x++) {
            placeColumnOnSurface(level, x, minZ, Direction.Axis.X);
            placeColumnOnSurface(level, x, maxZ, Direction.Axis.X);
        }
        for (int z = minZ; z <= maxZ; z++) {
            placeColumnOnSurface(level, minX, z, Direction.Axis.Z);
            placeColumnOnSurface(level, maxX, z, Direction.Axis.Z);
        }
    }

    private static void placeColumnOnSurface(ServerLevel level, int x, int z, Direction.Axis axis) {
        BlockState pane = ModBlocks.CHUNK_PORTAL.get().defaultBlockState().setValue(LimboPortalBlock.AXIS, axis);
        int baseY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
            m.set(x, baseY + dy, z);
            if (level.getBlockState(m).canBeReplaced()) level.setBlock(m, pane, 2);
        }
    }

    // --- per-portal emergence -------------------------------------------------------------------------

    private static void emerge(ServerLevel overworld, ChunkPortalData data) {
        long now = overworld.getGameTime();
        for (long key : data.portals()) {
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (!overworld.hasChunk(cx, cz)) continue; // only loaded portals emit (a player is near)
            Long next = data.nextSpawn().get(key);
            if (next == null) {
                data.nextSpawn().put(key, now + (long) (overworld.random.nextDouble() * EMERGE_INTERVAL));
                continue;
            }
            if (now < next) continue;
            data.nextSpawn().put(key, now + EMERGE_INTERVAL);
            spawnEmergence(overworld, cx, cz);
        }
    }

    /** Spawn one random Limbo-raid creature just outside a random wall of the portal at chunk (cx,cz). */
    private static void spawnEmergence(ServerLevel level, int cx, int cz) {
        int minX = cx << 4, maxX = minX + 15, minZ = cz << 4, maxZ = minZ + 15;
        RandomSource rng = level.random;
        int x, z;
        switch (rng.nextInt(4)) {
            case 0 -> { x = minX + rng.nextInt(16); z = minZ - 2; } // north (outside -Z)
            case 1 -> { x = minX + rng.nextInt(16); z = maxZ + 2; } // south (+Z)
            case 2 -> { x = minX - 2; z = minZ + rng.nextInt(16); } // west (-X)
            default -> { x = maxX + 2; z = minZ + rng.nextInt(16); } // east (+X)
        }
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
        Mob mob = randomRosterMob(level, rng);
        if (mob == null) return;
        mob.moveTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        mob.setPortalCooldown(); // don't let it instantly walk back into the wall it spawned by
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.PORTAL, x + 0.5, y + 1.0, z + 0.5, 24, 0.4, 0.6, 0.4, 0.25);
    }

    /** One random member of the Limbo-raid roster (LimboRaidManager.limboType + the wave phantom). Vex is a
     *  summon-only raid mob, so it is omitted here. */
    private static Mob randomRosterMob(ServerLevel level, RandomSource rng) {
        return switch (rng.nextInt(6)) {
            case 0 -> ModEntities.LIMBO_PILLAGER.get().create(level);
            case 1 -> ModEntities.LIMBO_VINDICATOR.get().create(level);
            case 2 -> ModEntities.LIMBO_EVOKER.get().create(level);
            case 3 -> ModEntities.LIMBO_WITCH.get().create(level);
            case 4 -> ModEntities.LIMBO_RAVAGER.get().create(level);
            default -> ModEntities.LIMBO_PHANTOM.get().create(level);
        };
    }

    // --- player walk-through (1:1, both directions) ---------------------------------------------------

    /** Move a player through a chunk-portal pane: overworld -> Limbo at the same x/z (building a landing floor +
     *  return ring the first time), or Limbo -> overworld at the same x/z (heightmap surface). */
    public static void crossPlayer(Player player, ServerLevel from, BlockPos portalPos) {
        if (!(player instanceof ServerPlayer sp)) return;
        MinecraftServer server = from.getServer();
        if (server == null) return;
        double x = Math.floor(player.getX()) + 0.5, z = Math.floor(player.getZ()) + 0.5;

        if (Level.OVERWORLD.equals(from.dimension())) {
            ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
            if (limbo == null) return;
            int cx = ((int) Math.floor(x)) >> 4, cz = ((int) Math.floor(z)) >> 4;
            int y = portalPos.getY(); // arrive level with the pane the player stepped through
            ensureLimboLanding(limbo, cx, cz, y);
            sp.teleportTo(limbo, x, y, z, sp.getYRot(), sp.getXRot());
        } else if (LimboDimension.LIMBO.equals(from.dimension())) {
            ServerLevel overworld = server.overworld();
            int y = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(x, 0, z)).getY();
            sp.teleportTo(overworld, x, y, z, sp.getYRot(), sp.getXRot());
        }
    }

    /** Build (once) the Limbo side of a chunk portal: a 16x16 fake-bedrock landing floor so the arrival is not a
     *  void drop, plus the matching 4-wall ring the player can walk back through. */
    private static void ensureLimboLanding(ServerLevel limbo, int cx, int cz, int y) {
        long key = ChunkPos.asLong(cx, cz);
        ChunkPortalData data = ChunkPortalData.get(limbo.getServer());
        if (data.isLimboRing(key)) return;

        int minX = cx << 4, maxX = minX + 15, minZ = cz << 4, maxZ = minZ + 15;
        BlockState floor = ModBlocks.FAKE_BEDROCK.get().defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                limbo.setBlock(m.set(bx, y - 1, bz), floor, 2);
            }
        }
        for (int bx = minX; bx <= maxX; bx++) {
            placeLimboColumn(limbo, bx, minZ, y, Direction.Axis.X);
            placeLimboColumn(limbo, bx, maxZ, y, Direction.Axis.X);
        }
        for (int bz = minZ; bz <= maxZ; bz++) {
            placeLimboColumn(limbo, minX, bz, y, Direction.Axis.Z);
            placeLimboColumn(limbo, maxX, bz, y, Direction.Axis.Z);
        }
        data.addLimboRing(key);
    }

    private static void placeLimboColumn(ServerLevel limbo, int x, int z, int baseY, Direction.Axis axis) {
        BlockState pane = ModBlocks.CHUNK_PORTAL.get().defaultBlockState().setValue(LimboPortalBlock.AXIS, axis);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < WALL_HEIGHT; dy++) {
            limbo.setBlock(m.set(x, baseY + dy, z), pane, 2);
        }
    }
}
