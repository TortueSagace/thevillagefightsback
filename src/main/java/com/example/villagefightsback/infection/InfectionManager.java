package com.example.villagefightsback.infection;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.BuilderRole;
import com.example.villagefightsback.BrainNodeData;
import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.goal.MinerGoal;
import com.example.villagefightsback.wall.WallBlock;
import com.example.villagefightsback.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Drives the brain node block infection (level 2+). Designed to stay cheap at any zone size:
 *  - Path growth (spec 1) touches only the &le;100 newest path blocks, weighted to extend in a line.
 *  - Conversion (spec 2-3) walks an ACTIVE FRONTIER under a fixed per-tick budget (round-robin), so cost is
 *    bounded by the budget regardless of total converted area; interior blocks retire out of the frontier.
 *  - The same frontier walk doubles as destruction detection: a frontier cell whose block has vanished and
 *    was not a sanctioned removal (player break / miner dig) is treated as an ATTACK -> local speed-up (spec 8).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class InfectionManager {
    private InfectionManager() {}

    private static final int PROCESS_INTERVAL = 5;   // run path+conversion every 5 ticks (slow, low-lag)
    private static final int MANAGE_INTERVAL = 20;   // seed villages (cheap guard) every 1s
    private static final int STAFF_INTERVAL = 100;   // (re)staff miners every 5s -- the only entity scan, kept sparse
    private static final int START_LEVEL = 2;        // infection begins at brainNode level 2

    private static final double PATH_GROW_CHANCE = 0.60;  // per head per pass -- paths must clearly outrun conversion
    private static final double CONV_CHANCE = 0.15;       // per frontier cell per pass (well below paths so paths lead)
    // Process the WHOLE active frontier each pass (symmetric growth on every edge) up to this hard cap; the
    // frontier converges to the active perimeter (interior cells retire), so this stays cheap until very large
    // zones, where the cap bounds per-tick cost. (The old tiny budget made growth slow + lopsided.)
    private static final int MAX_CELLS_PER_PASS = 8192;
    private static final double SAND_DROP_CHANCE = 0.04;  // a stuck path over air/water drips fertile sand
    private static final float RICH_CHANCE = 0.05F;       // converted block: 5% rich, else fertile
    private static final long BOOST_DURATION = 600L;      // +100% for 30s near a lost block (spec 8)
    private static final double SLOW_FACTOR = 0.1;        // steady-state infection is 10x slower than the raw
                                                          // chances; SKIPPED only while fighting back (a boost).

    private static final int SEED_RADIUS = 24;       // one-time scan for dirt paths around a new lvl-2 village
    private static final int SEED_Y = 5;

    // Spatial bound: nothing is infected beyond a brain node's range (horizontal). The reach scales with stage
    // -- stage 2 = 1/3, stage 3 = 2/3, stage 4+ = the full range -- so the takeover grows as the village does.
    private static final double INFECT_RANGE_FULL = 100.0; // full (stage-4) brainNode infection radius
    private static final double MINER_FRACTION = 0.40;
    private static final double MINER_AREA = 64.0;

    // 26-neighbourhood offsets (immediate +/-1 incl. diagonals).
    private static final int[][] N26;
    static {
        List<int[]> o = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0) o.add(new int[]{dx, dy, dz});
        N26 = o.toArray(new int[0][]);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Two independent spreads share this engine: the VILLAGE brain-node fertile spread (enable_block_infection)
        // and the EMERALD faction's infested-path spread (enable_emerald_infestation).
        boolean village = com.example.villagefightsback.Config.blockInfectionEnabled();
        boolean emerald = com.example.villagefightsback.Config.emeraldInfestationEnabled();
        if (!village && !emerald) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long tc = server.getTickCount();
        long _p = com.example.villagefightsback.ModProfiler.now();

        if (village && tc % MANAGE_INTERVAL == 0) {            // seeding/staffing is village-only
            boolean doStaff = (tc % STAFF_INTERVAL == 0); // entity scans only every 5s
            BrainNodeData gdata = BrainNodeData.get(server);
            for (GlobalPos gp : new ArrayList<>(gdata.blocks())) {
                ServerLevel level = server.getLevel(gp.dimension());
                if (level == null || !level.isLoaded(gp.pos())) continue;
                if (gdata.econ(gp).getLevel() < START_LEVEL) continue;
                InfectionData inf = InfectionData.get(level);
                if (!inf.hasSeeded(gp.pos().asLong())) seedVillage(level, inf, gp, gdata);
                if (doStaff) staffMiners(level, gp.pos());
            }
        }

        if (tc % PROCESS_INTERVAL == 0) {
            BrainNodeData gdata = BrainNodeData.get(server);
            for (ServerLevel level : server.getAllLevels()) {
                InfectionData inf = InfectionData.get(level);
                long now = level.getGameTime();
                inf.pruneBoosts(now);
                inf.cleanupSanctioned(now);
                double[][] brain = village ? brainNodeRanges(gdata, level) : EMPTY_RANGES; // {cx,cz,rSqr,speed=1.0}
                LAST_BRAIN.put(level.dimension(), brain);   // snapshot for the cold callers (fertile grass)
                java.util.List<Long> infIds = new ArrayList<>();
                double[][] ranges = emerald ? concat(brain, infestedRanges(inf, brain, infIds)) : brain; // + slow infested
                if (ranges.length == 0) continue;                  // nothing active here -> nothing to infect
                // The village's own buildings are off limits. Fetched ONCE per pass and threaded down (exactly
                // like ranges) so the 8192-cell walk never repeats the SavedData lookup.
                StructureZones zones = StructureZones.get(level);
                // The players' own claims. refreshActive resolves each sign's placer against their standing
                // ONCE here, so the hot query below is a plain set lookup rather than a reputation read.
                MarkerZones marks = MarkerZones.get(level);
                marks.refreshActive(server);
                // Dirt paths still grow only within brain ranges; conversion spreads across both, but the convert
                // chance is scaled by speedAt() so infested-only ground proliferates at 1/5 the brain rate.
                growPaths(level, inf, now, brain, zones, marks);
                // Which infested paths still had ground to take this pass. Marked inside speedAt, which convert()
                // only reaches once findCandidate has already found something convertible -- so a set bit means
                // "this path is still working" and a clear one means "it found nothing", at no extra cost.
                boolean[] worked = new boolean[infIds.size()];
                convert(level, inf, now, ranges, zones, marks, brain.length, worked);
                if (!infIds.isEmpty()) retireExhausted(level, inf, infIds, worked);
            }
        }
        com.example.villagefightsback.ModProfiler.add("infection", _p);
    }

    /** Per-brainNode infection reach in {@code level}: {centreX+0.5, centreZ+0.5, rangeSqr} for each brainNode
     *  at stage >= 2, with the radius scaled by stage (2 -> 1/3, 3 -> 2/3, 4+ -> full). */
    private static double[][] brainNodeRanges(BrainNodeData gdata, ServerLevel level) {
        List<double[]> out = new ArrayList<>();
        for (GlobalPos gp : gdata.blocks()) {
            if (!gp.dimension().equals(level.dimension())) continue;
            int stage = gdata.econ(gp).getLevel();
            if (stage < START_LEVEL) continue;
            double factor = Math.min(1.0, (stage - 1) / 3.0); // stage2=1/3, stage3=2/3, stage4+=1
            // Base reach scaled by the brain influence-radius multiplier AND the infection-reach multiplier; the
            // per-stage factor still applies on top.
            double r = INFECT_RANGE_FULL * com.example.villagefightsback.Config.brainRadiusMult()
                    * com.example.villagefightsback.Config.infectionReachMult() * factor;
            out.add(new double[]{gp.pos().getX() + 0.5, gp.pos().getZ() + 0.5, r * r, 1.0}); // speed 1.0 = full
        }
        return out.toArray(new double[0][]);
    }

    private static final double INFESTED_SPEED = 1.0 / 3.0; // infested paths proliferate at a uniform 1/3 of the brain rate
    private static final double[][] EMPTY_RANGES = new double[0][];

    /** Per-infested-path proliferation reach: a full stage-4 radius around each tracked infested path block, but at
     *  the slow speed factor -- so the emerald faction's blocks spread on their own (away from any village) at 1/5
     *  the rate, reusing the same growth/conversion engine. */
    private static double[][] infestedRanges(InfectionData inf, double[][] brain, java.util.List<Long> idsOut) {
        List<double[]> out = new ArrayList<>();
        double r = INFECT_RANGE_FULL * com.example.villagefightsback.Config.brainRadiusMult()
                * com.example.villagefightsback.Config.infectionReachMult();
        double rr = r * r;
        // A path only proliferates if a stage-2+ node can still see it. Without this an infested path is an
        // independent origin with no village behind it, and a single one dropped far from anywhere converts a
        // full radius of map on its own. There are at most 64 tracked paths, so testing each against the
        // already-computed brain array costs nothing.
        boolean needNode = com.example.villagefightsback.Config.infestedNeedsBrainNode();
        for (long l : inf.infestedSnapshot()) {
            BlockPos p = BlockPos.of(l);
            double px = p.getX() + 0.5, pz = p.getZ() + 0.5;
            if (needNode && !inRange(px, pz, brain)) continue;
            out.add(new double[]{px, pz, rr, INFESTED_SPEED});
            if (idsOut != null) idsOut.add(l);   // parallel to `out`, so a range index maps back to its path
        }
        return out.toArray(new double[0][]);
    }

    /**
     * How many consecutive passes an active infested path may find nothing before it gives up its slot.
     *
     * <p>Not one: a path can come up empty simply because the frontier walk hit its per-pass cell budget
     * elsewhere. At the 5-tick pass interval this is about ten seconds of genuinely finding nothing, which a
     * path that still has ground to take will not manage.
     */
    private static final int IDLE_PASSES_TO_RETIRE = 40;

    /** dimension -> (infested path -> consecutive passes it has found nothing). Transient, rebuilt as it goes. */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            java.util.Map<Long, Integer>> IDLE = new java.util.HashMap<>();

    /**
     * Hand the slots of finished paths to the ones waiting for a turn.
     *
     * <p>An infested path that has converted everything within its reach goes on being scanned every pass for
     * nothing, while paths queued behind it never get to spread at all. When one has found nothing for
     * {@value #IDLE_PASSES_TO_RETIRE} passes it is retired and the longest-waiting path takes its place.
     *
     * <p>Cheap by construction: the only per-conversion work is a bit already set inside speedAt, and this runs
     * once per pass over at most {@link InfectionData#INFESTED_CAP} entries.
     */
    private static void retireExhausted(ServerLevel level, InfectionData inf,
                                        java.util.List<Long> ids, boolean[] worked) {
        if (inf.waitingInfestedCount() == 0) return;   // nobody waiting -> churning slots buys nothing
        java.util.Map<Long, Integer> idle = IDLE.computeIfAbsent(level.dimension(), k -> new java.util.HashMap<>());
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            if (worked[i]) { idle.remove(id); continue; }
            int n = idle.merge(id, 1, Integer::sum);
            if (n < IDLE_PASSES_TO_RETIRE) continue;
            idle.remove(id);
            Long promoted = inf.retireInfestedPath(id);
            if (promoted != null) {
                TheVillageFightsBack.LOGGER.debug(
                        "[The Village Fights Back] infested path at {} is spent; {} takes its slot ({} still waiting).",
                        BlockPos.of(id), BlockPos.of(promoted), inf.waitingInfestedCount());
            }
            if (inf.waitingInfestedCount() == 0) return;   // queue drained; leave the rest of the active set alone
        }
        // Forget counters for paths that are no longer active. Only worth the allocation when there is
        // actually something stale: the map gains a key only for a path in `ids`, so an equal size means clean.
        if (idle.size() > ids.size()) idle.keySet().retainAll(new java.util.HashSet<>(ids));
    }

    /**
     * The brain ranges last computed for each dimension, so a COLD caller can ask "is this inside a stage-2+
     * node's reach?" without recomputing them.
     *
     * <p>{@link com.example.villagefightsback.FertileGrassBlock} is the reason this exists: it spreads from a
     * vanilla random tick, which is far too frequent to walk the node list (and
     * {@code BrainNodeEvents.nearestNodeTo} is documented as cold-path only). Refreshed once per pass here, read
     * there. A snapshot up to 5 ticks stale is fine for something that fires at random anyway.
     */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            double[][]> LAST_BRAIN = new java.util.HashMap<>();

    /** True if {@code pos} is inside some stage-2+ brain node's infection reach, per the last pass's snapshot. */
    public static boolean withinBrainReach(ServerLevel level, BlockPos pos) {
        double[][] brain = LAST_BRAIN.get(level.dimension());
        if (brain == null || brain.length == 0) return false;
        return inRange(pos.getX() + 0.5, pos.getZ() + 0.5, brain);
    }

    /** Drop the snapshot with the server, so a second world never reads the first one's node positions. */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        LAST_BRAIN.clear();
        IDLE.clear();
    }

    private static double[][] concat(double[][] a, double[][] b) {
        if (a.length == 0) return b;
        if (b.length == 0) return a;
        double[][] out = new double[a.length + b.length][];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** The proliferation speed factor for (x,z): the SLOWEST covering source wins. So wherever an infested path
     *  reaches, conversion runs at the UNIFORM infested rate ({@link #INFESTED_SPEED} = 1/3) even if a brain node
     *  also covers that cell; a cell covered ONLY by a brain runs at full speed (1.0). 0 if no source covers it.
     *  (Scans all ranges -- the convert pass is throttled, so this stays cheap.) */
    private static double speedAt(double x, double z, double[][] ranges) {
        return speedAt(x, z, ranges, 0, null);
    }

    /**
     * As above, and -- when {@code worked} is given -- notes every INFESTED source covering this cell.
     *
     * <p>The loop runs either way, so the bookkeeping is free. {@code brainCount} is where the infested ranges
     * start in {@code ranges} (they are concatenated after the brain nodes), so {@code i - brainCount} indexes
     * {@code worked} and, through it, the path list built alongside them.
     */
    private static double speedAt(double x, double z, double[][] ranges, int brainCount, boolean[] worked) {
        double best = 0.0; // 0 = no source covers this cell
        for (int i = 0; i < ranges.length; i++) {
            double[] g = ranges[i];
            double dx = x - g[0], dz = z - g[1];
            if (dx * dx + dz * dz <= g[2]) {
                if (worked != null && i >= brainCount) worked[i - brainCount] = true;
                double sp = g.length > 3 ? g[3] : 1.0;
                if (best == 0.0 || sp < best) best = sp; // the slowest covering source governs (uniform infested rate)
            }
        }
        return best;
    }

    /** True if (x,z) lies within any brain node's (stage-scaled) infection range. */
    private static boolean inRange(double x, double z, double[][] ranges) {
        for (double[] g : ranges) {
            double dx = x - g[0], dz = z - g[1];
            if (dx * dx + dz * dz <= g[2]) return true;
        }
        return false;
    }

    /** Player breaks an infection block -> sanctioned (NOT an attack); also drop it from the rich registry. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        BlockState s = event.getState();
        if (!BlockMatcher.isInfection(s)) return;
        InfectionData inf = InfectionData.get(sl);
        long key = event.getPos().asLong();
        inf.sanction(key, sl.getGameTime());
        if (BlockMatcher.isConverted(s)) inf.decConverted(); // a fertilized block cleared -> frees the cap (paths don't count)
        if (BlockMatcher.isRich(s)) inf.removeRich(key);
    }

    /** Explosions: every infection block they destroy is an ATTACK unless a player/villager caused the blast. */
    @SubscribeEvent
    public static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        net.minecraft.world.entity.Entity cause = event.getExplosion().getDirectSourceEntity();
        boolean friendly = cause instanceof net.minecraft.world.entity.player.Player || cause instanceof Villager;
        InfectionData inf = InfectionData.get(sl);
        long now = sl.getGameTime();
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState bs = sl.getBlockState(pos);
            if (!BlockMatcher.isInfection(bs)) continue;
            long key = pos.asLong();
            inf.removeRich(key);
            if (BlockMatcher.isConverted(bs)) inf.decConverted();    // count only fertilized blocks (not paths)
            inf.sanction(key, now);                                   // accounted for -> frontier scan won't re-boost
            if (!friendly) inf.addBoost(key, now + BOOST_DURATION);   // the ground feels attacked (spec 8)
            inf.retire(key);                                          // it's gone -> drop it from the frontier
            reactivateNeighbours(inf, sl, pos);                       // wake the surviving rim to reclaim the crater
        }
    }

    /** A miner that dies frees its slot immediately (the emerald drops via its 1.0 drop chance); the next
     *  staffMiners pass promotes a replacement (spec 6). */
    @SubscribeEvent
    public static void onMinerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager v && MinerRole.isMiner(v)) MinerRole.demote(v);
    }

    /** A non-villager mob destroying an infection block (e.g. griefing) is an ATTACK. */
    @SubscribeEvent
    public static void onMobDestroyBlock(net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
        if (!BlockMatcher.isInfection(event.getState())) return;
        InfectionData inf = InfectionData.get(sl);
        long key = event.getPos().asLong();
        long now = sl.getGameTime();
        inf.removeRich(key);
        if (BlockMatcher.isConverted(event.getState())) inf.decConverted(); // count only fertilized blocks (not paths)
        inf.sanction(key, now);
        if (!(event.getEntity() instanceof Villager)) inf.addBoost(key, now + BOOST_DURATION);
        inf.retire(key);
        reactivateNeighbours(inf, sl, event.getPos());
    }

    /**
     * Lay an infested path at {@code pos} (the block right below an emerald-converted unit), if emerald infestation
     * is enabled (enable_emerald_infestation) and that block is softer than ancient debris. Registers it as a slow,
     * full-radius proliferation source (5x slower than a brain node). (v1.0.3 mob infection.)
     */
    /** The coloured twin of one of our blocks, or the green one when that colour does not exist. */
    static net.minecraft.world.level.block.Block coloured(String greenName, String colour,
                                                          net.minecraft.world.level.block.Block green) {
        if (colour == null || colour.isEmpty()) return green;
        net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.Block,
                net.minecraft.world.level.block.Block> c =
                com.example.villagefightsback.ModBlocks.COLOURED.get(greenName + colour);
        return c != null ? c.get() : green;
    }

    public static void placeInfestedPath(ServerLevel level, BlockPos pos) {
        // An emerald unit dying on a player's claim must not leave a proliferation source behind: this is the
        // one infested-path write that does not come from the throttled pass, so it carries its own check.
        if (MarkerZones.get(level).blocked(pos.getX(), pos.getY(), pos.getZ())) return;
        if (!com.example.villagefightsback.Config.emeraldInfestationEnabled()) return;
        if (!level.isLoaded(pos)) return;
        if (StructureZones.get(level).contains(pos)) return;   // never inside one of the village's own buildings
        BlockState cur = level.getBlockState(pos);
        if (cur.is(com.example.villagefightsback.ModBlocks.INFESTED_PATH.get())) return;        // already ours
        if (cur.getBlock().getExplosionResistance() >= Blocks.ANCIENT_DEBRIS.getExplosionResistance()) return; // too tough
        if (cur.hasBlockEntity()) return;   // never convert chests / containers / other block entities
        // A path picks its colour at random when it is laid, and everything it goes on to convert inherits it.
        // That is what gives the map zones of different colours instead of one uniform green.
        String[] all = com.example.villagefightsback.infection.BlockMatcher.ALL_SUFFIXES;
        // One of the two places a colour is CHOSEN (the other is BlockMatcher.colourOf, which inherits one).
        // Both answer green when the player has asked for a monogreen world.
        String colour = com.example.villagefightsback.Config.monochromeGreen()
                ? "" : all[level.getRandom().nextInt(all.length)];
        level.setBlock(pos, coloured("infested_path", colour,
                com.example.villagefightsback.ModBlocks.INFESTED_PATH.get()).defaultBlockState(), 3);
        InfectionData.get(level).addInfestedPath(pos.asLong());
    }

    /**
     * Tell the engine that every infection block in this volume is being removed BY THE VILLAGE (a structure is
     * about to be built there), so the frontier walk does not read the carve as an ATTACK and boost the infection
     * all around the new building (spec 8). Also drops the cells from the frontier / path heads and corrects the
     * fertilized tally, which the walk would otherwise only fix lazily on its next visit.
     *
     * <p>Bounded and cold: runs once per building, over that building's own volume -- the same volume the caller
     * is about to overwrite anyway. Bounds are INCLUSIVE.
     */
    public static void forgetVolume(ServerLevel level, int minX, int minY, int minZ,
                                    int maxX, int maxY, int maxZ) {
        InfectionData inf = InfectionData.get(level);
        long now = level.getGameTime();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    m.set(x, y, z);
                    if (!level.isLoaded(m)) continue;
                    BlockState s = level.getBlockState(m);
                    if (!BlockMatcher.isInfection(s)) continue;
                    long key = m.asLong();
                    inf.sanction(key, now);                                // a sanctioned removal, not an attack
                    inf.dropPathHead(key);
                    inf.retire(key);
                    inf.removeRich(key);
                    if (BlockMatcher.isConverted(s)) inf.decConverted();
                }
            }
        }
    }

    /** Infested path blocks within {@code radius} of {@code center} (from the tracked set, verified still present).
     *  Used by emerald-unit death effects (v1.0.3). Cheap: scans the bounded infested set, not the world. */
    public static java.util.List<BlockPos> infestedPathsWithin(ServerLevel level, BlockPos center, double radius) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        double r2 = radius * radius;
        InfectionData inf = InfectionData.get(level);
        for (long l : inf.infestedSnapshot()) {
            BlockPos p = BlockPos.of(l);
            if (p.distSqr(center) > r2 || !level.isLoaded(p)) continue;
            if (level.getBlockState(p).is(com.example.villagefightsback.ModBlocks.INFESTED_PATH.get())) out.add(p);
        }
        return out;
    }

    // --- seeding (one-time per village when it reaches level 2) -------------------------------------
    private static void seedVillage(ServerLevel level, InfectionData inf, GlobalPos gp, BrainNodeData data) {
        BlockPos center = gp.pos();
        inf.markSeeded(center.asLong());

        // Stop building: demote nearby builders (they go idle; staffMiners then makes 20% of them miners).
        for (Villager v : level.getEntitiesOfClass(Villager.class,
                new AABB(center).inflate(96.0), v -> v.isAlive())) {
            BuilderRole.demote(v);
        }

        // The brainNode finishes the wall itself (no builders any more), then lays a dirt-path ring along the
        // wall footprint so the infection escapes the envelope and spreads outward.
        raiseWallAndSeedRing(level, inf, gp, data);

        // Also seed from any existing village dirt paths near the centre.
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -SEED_RADIUS; dx <= SEED_RADIUS; dx++) {
            for (int dz = -SEED_RADIUS; dz <= SEED_RADIUS; dz++) {
                for (int dy = -SEED_Y; dy <= SEED_Y; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isLoaded(m) && level.getBlockState(m).is(Blocks.DIRT_PATH)) {
                        inf.addPathHead(m.asLong());
                    }
                }
            }
        }
    }

    /**
     * The brain node raises whatever is left of its wall itself and lays the outward dirt-path ring along the
     * foundation, which is how the infection escapes the village envelope.
     *
     * <p>Split out of {@link #seedVillage} because a village can acquire its wall plan AFTER it has already been
     * seeded: a footprint too sprawling to plan concavely used to yield no plan at all, and the node could reach
     * stage 2 with none. BrainNodeEvents now surveys such a village even at stage 2 and calls this the moment a
     * plan finally exists, so the wall and the ring are not lost just because they arrived late.
     *
     * <p>Idempotent: a plan already marked complete is dropped by the caller, and every write is a gap-fill.
     */
    public static void raiseWallAndSeedRing(ServerLevel level, InfectionData inf, GlobalPos gp,
                                            BrainNodeData data) {
        WallPlan plan = data.getWallPlan(gp);
        if (plan == null) return;
        BlockPos center = gp.pos();
        for (WallBlock wb : plan.blocks()) {
            BlockPos p = wb.pos();
            if (!level.isLoaded(p)) continue;
            if (!plan.satisfied(level, wb) && level.getBlockState(p).canBeReplaced()) {
                level.setBlock(p, wb.toState(), 3);                 // finish remaining gaps (no bulldozing)
            }
            if (wb.course() == 0) seedWallPath(level, inf, p, center); // path just outside the foundation
        }
        plan.markComplete();
        com.example.villagefightsback.wall.WallBuild.clearPlan(gp);
        data.setDirty();
    }

    /** Lay a dirt path at the surface just OUTSIDE a wall foundation column (so it can spread beyond the wall;
     *  a path can't survive directly under the solid wall block, so it sits one step out at ground level). */
    private static void seedWallPath(ServerLevel level, InfectionData inf, BlockPos foundation, BlockPos brainNode) {
        int dx = foundation.getX() - brainNode.getX();
        int dz = foundation.getZ() - brainNode.getZ();
        Direction out = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        int ex = foundation.getX() + out.getStepX(), ez = foundation.getZ() + out.getStepZ();
        for (int y = foundation.getY() + 1; y >= foundation.getY() - 3; y--) { // find the exterior surface
            BlockPos g = new BlockPos(ex, y, ez);
            if (!level.isLoaded(g)) continue;
            BlockState gs = level.getBlockState(g);
            if (gs.isAir() || !gs.getFluidState().isEmpty() || !gs.blocksMotion()) continue; // need solid ground
            if (BlockMatcher.isInfection(gs)) return;                  // already ours
            if (StructureZones.get(level).contains(g)) return;         // never seed a path inside a building
            if (MarkerZones.get(level).blocked(g.getX(), g.getY(), g.getZ())) return; // ...nor on a player's claim
            BlockState above = level.getBlockState(g.above());
            if (above.isAir() || !above.getFluidState().isEmpty() || !above.blocksMotion()) {
                // The village's own path, in a random colour: this is the one place a village-only world
                // gets any colour at all, since the coloured infested path only ever appears where an emerald
                // unit died. Identical to an infested path to look at; the difference is that the brain cannot
                // spawn emerald units above it, which follows from it never entering the tracked infested set.
                String[] all = BlockMatcher.ALL_SUFFIXES;
                String pathColour = com.example.villagefightsback.Config.monochromeGreen()
                        ? "" : all[level.getRandom().nextInt(all.length)];
                level.setBlock(g, coloured("village_path", pathColour,
                        com.example.villagefightsback.ModBlocks.VILLAGE_PATH.get()).defaultBlockState(), 3);
                inf.addPathHead(g.asLong());
                return;
            }
        }
    }

    // --- path growth (spec 1 + spec 5) --------------------------------------------------------------
    private static final Direction[] HORIZ = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private static void growPaths(ServerLevel level, InfectionData inf, long now, double[][] ranges,
                                  StructureZones zones, MarkerZones marks) {
        RandomSource rnd = level.getRandom();
        for (long head : inf.pathHeadsSnapshot()) {
            BlockPos p = BlockPos.of(head);
            if (!level.isLoaded(p)) continue;
            if (!BlockMatcher.isPath(level.getBlockState(p))) { inf.dropPathHead(head); continue; }
            double mult = inf.multiplierAt(p, now);
            double slow = (mult > 1.0) ? 1.0 : SLOW_FACTOR; // not slowed while fighting back
            double chance = Math.min(1.0, PATH_GROW_CHANCE * mult * slow * com.example.villagefightsback.Config.infectionSpeedMult());
            if (rnd.nextDouble() >= chance) continue;
            int res = tryGrow(level, inf, p, rnd, ranges, zones, marks);
            if (res < 0) {
                // spec 5: a path with NO viable extension, sitting over air/water, may drip a fertile sand below
                BlockPos below = p.below();
                if (level.isLoaded(below) && !zones.contains(below)
                        && !marks.blocked(below.getX(), below.getY(), below.getZ())) {
                    BlockState bs = level.getBlockState(below);
                    boolean voidOrWater = bs.isAir() || !bs.getFluidState().isEmpty();
                    if (voidOrWater && rnd.nextDouble() < SAND_DROP_CHANCE && BlockMatcher.fertileSand() != null) {
                        // the drip matches the path that produced it
                        level.setBlock(below, coloured("fertile_sand",
                                BlockMatcher.colourOf(level.getBlockState(p)),
                                BlockMatcher.fertileSand()).defaultBlockState(), 3);
                        inf.incConverted();
                    }
                }
            }
        }
    }

    /**
     * Try to extend the path from {@code p}. Direction odds are ABSOLUTE over all four horizontals (45/45/5/5
     * along the path's existing axis, 25 each if no axis yet), matching the spec: the side that already holds
     * a path consumes its 45% as a no-op, so the path mostly continues forward and rarely branches.
     * Returns 1 = extended, 0 = picked a non-viable direction (no-op this attempt), -1 = no viable extension.
     */
    private static int tryGrow(ServerLevel level, InfectionData inf, BlockPos p, RandomSource rnd,
                               double[][] ranges, StructureZones zones, MarkerZones marks) {
        int xCount = 0, zCount = 0;
        for (Direction d : HORIZ) if (hasPathNeighbour(level, p, d)) {
            if (d.getAxis() == Direction.Axis.X) xCount++; else zCount++;
        }
        boolean tie = xCount == zCount;
        boolean xDominant = xCount > zCount;

        BlockPos[] targets = new BlockPos[HORIZ.length];
        double[] w = new double[HORIZ.length];
        double total = 0;
        boolean anyViable = false;
        for (int i = 0; i < HORIZ.length; i++) {
            targets[i] = extendTarget(level, p, HORIZ[i], ranges, zones, marks);
            if (targets[i] != null) anyViable = true;
            boolean alongX = HORIZ[i].getAxis() == Direction.Axis.X;
            w[i] = tie ? 25.0 : ((xDominant == alongX) ? 45.0 : 5.0);
            total += w[i];
        }
        if (!anyViable) return -1; // genuinely stuck (spec 5 sand-drop candidate)

        double r = rnd.nextDouble() * total;
        int chosen = HORIZ.length - 1;
        for (int i = 0; i < HORIZ.length; i++) { r -= w[i]; if (r <= 0) { chosen = i; break; } }
        if (targets[chosen] == null) return 0; // the weighted pick was a no-op direction this attempt
        level.setBlock(targets[chosen], Blocks.DIRT_PATH.defaultBlockState(), 3);
        inf.addPathHead(targets[chosen].asLong());
        return 1;
    }

    private static boolean hasPathNeighbour(ServerLevel level, BlockPos p, Direction d) {
        BlockPos n = p.relative(d);
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos q = n.above(dy);
            if (level.isLoaded(q) && level.getBlockState(q).is(Blocks.DIRT_PATH)) return true;
        }
        return false;
    }

    /** The ground cell (within +/-2 in Y) the path would convert to dirt-path in direction {@code d}, or null.
     *  Never extends beyond a brain node's (stage-scaled) range. */
    private static BlockPos extendTarget(ServerLevel level, BlockPos p, Direction d, double[][] ranges,
                                         StructureZones zones, MarkerZones marks) {
        int nx = p.getX() + d.getStepX(), nz = p.getZ() + d.getStepZ();
        if (!inRange(nx + 0.5, nz + 0.5, ranges)) return null;       // outside every brain node's reach -> stop here
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos g = new BlockPos(nx, p.getY() + dy, nz);
            if (!level.isLoaded(g)) continue;
            if (zones.contains(nx, g.getY(), nz)) continue;           // never pave through one of our own buildings
            if (marks.blocked(nx, g.getY(), nz)) continue;            // nor across ground a player has claimed
            BlockState gs = level.getBlockState(g);
            if (!BlockMatcher.isPathGround(gs)) continue;            // must be dirt-like ground (not already a path)
            BlockState above = level.getBlockState(g.above());
            if (above.isAir() || !above.getFluidState().isEmpty() || !above.blocksMotion()) return g; // walkable surface
        }
        return null;
    }

    // --- conversion (spec 2-3) + destruction detection (spec 8) -------------------------------------
    private static void convert(ServerLevel level, InfectionData inf, long now, double[][] ranges,
                                StructureZones zones, MarkerZones marks,
                                int brainCount, boolean[] worked) {
        // The boost multiplier applies to the per-cell CHANCE only (below), not the cell count, so the per-tick
        // cost stays bounded regardless of speed-ups (anti-lag).
        int n = Math.min(MAX_CELLS_PER_PASS, inf.frontierSize());
        RandomSource rnd = level.getRandom();
        for (int i = 0; i < n; i++) {
            Long popped = inf.pollFrontier();
            if (popped == null) break;
            long key = popped;
            BlockPos pos = BlockPos.of(key);
            if (!level.isLoaded(pos)) { inf.reactivate(key); continue; } // can't see it now; revisit later

            BlockState s = level.getBlockState(pos);
            if (!BlockMatcher.isInfection(s)) {
                // The block is gone. If nobody sanctioned its removal, the ground "feels attacked".
                if (!inf.wasSanctioned(key, now)) inf.addBoost(key, now + BOOST_DURATION);
                inf.removeRich(key);
                inf.decConverted();                    // one fewer of our blocks -> frees the cap to regrow
                inf.retire(key);
                reactivateNeighbours(inf, level, pos); // re-take the exposed ground (heals craters / damage)
                continue;
            }

            // The leading path tips (the <=100 newest path blocks) do NOT fertilize, so the dirt paths keep
            // racing ahead onto fresh ground and conversion fills in BEHIND them (spec 3). Keep them active.
            if (inf.isPathHead(key)) { inf.reactivate(key); continue; }

            BlockPos cand = findCandidate(level, pos, rnd, ranges, zones, marks);
            if (cand == null) { inf.retire(key); continue; }           // nothing left to convert here -> drop it

            double mult = inf.multiplierAt(pos, now);
            double slow = (mult > 1.0) ? 1.0 : SLOW_FACTOR;            // not slowed while fighting back
            double speed = speedAt(pos.getX() + 0.5, pos.getZ() + 0.5, ranges, brainCount, worked); // 1.0 near a brain, 0.2 infested-only
            double chance = Math.min(1.0, CONV_CHANCE * mult * slow * speed
                    * com.example.villagefightsback.Config.infectionSpeedMult());
            if (rnd.nextDouble() < chance) {
                BlockState src = level.getBlockState(cand);
                // The colour of the block we are spreading FROM. That is the whole mechanism: a zone keeps one
                // colour because it grows from one origin, and nothing has to remember anything. A brain node
                // seeding its first cell is not one of ours, so it answers green.
                String colour = BlockMatcher.colourOf(level.getBlockState(pos));
                if (BlockMatcher.isWoodenDoor(src)) {                   // doors: replace BOTH halves at once
                    placeFertileDoor(level, cand, src, colour);
                    inf.addFrontier(cand.asLong());
                    inf.incConverted();
                } else {
                    boolean richVariant = rnd.nextFloat() < RICH_CHANCE;
                    BlockState conv = BlockMatcher.convertedState(src, richVariant, colour);
                    if (conv != null) {
                        // Any small plant next to this new fertile block becomes fertile grass. Done BEFORE the
                        // soil flips so a plant standing on it is replaced (and survives) rather than popping off.
                        convertNearbyPlants(level, inf, cand, zones, marks, colour);
                        conv = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(conv, level, cand);
                        // Flag 3 notifies neighbours, which makes anything resting on this cell re-check its
                        // support -- and redstone dust or a repeater sitting on top would drop, breaking the
                        // circuit the infection just passed under. The fertile block is a full solid cube like
                        // the one it replaces, so the component is still perfectly legal; it is only the update
                        // that would have removed it. Placing with UPDATE_CLIENTS alone converts the ground and
                        // leaves the contraption standing.
                        int flags = BlockMatcher.isRedstone(level.getBlockState(cand.above())) ? 2 : 3;
                        level.setBlock(cand, conv, flags); // updateFromNeighbourShapes -> fertile fences keep connections
                        inf.addFrontier(cand.asLong());
                        inf.incConverted();
                        if (richVariant) inf.addRich(cand.asLong());
                        // Sparse flora: a tiny chance to sprout a fertile tree / chorus on the new fertile ground.
                        FertileFlora.onConverted(level, cand, zones, marks, colour);
                    }
                }
            }
            inf.reactivate(key); // may still have other candidates next time
        }
    }

    /** Replace a (wooden) door with the fertile door -- BOTH halves -- preserving facing/hinge/open/powered. */
    private static void placeFertileDoor(ServerLevel level, BlockPos cell, BlockState src, String colour) {
        net.minecraft.world.level.block.state.properties.DoubleBlockHalf half =
                src.getValue(net.minecraft.world.level.block.DoorBlock.HALF);
        BlockPos lower = half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                ? cell : cell.below();
        BlockState lowerSrc = level.getBlockState(lower);
        if (!(lowerSrc.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) return;
        BlockState fd = coloured("fertile_door", colour,
                com.example.villagefightsback.ModBlocks.FERTILE_DOOR.get()).defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING,
                        lowerSrc.getValue(net.minecraft.world.level.block.DoorBlock.FACING))
                .setValue(net.minecraft.world.level.block.DoorBlock.HINGE,
                        lowerSrc.getValue(net.minecraft.world.level.block.DoorBlock.HINGE))
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN,
                        lowerSrc.getValue(net.minecraft.world.level.block.DoorBlock.OPEN))
                .setValue(net.minecraft.world.level.block.DoorBlock.POWERED,
                        lowerSrc.getValue(net.minecraft.world.level.block.DoorBlock.POWERED));
        // Place BOTH halves with UPDATE_KNOWN_SHAPE so the door's shape self-validation never runs mid-swap:
        // with plain flag 3, setting the lower while the old (oak) upper is still there makes the door logic
        // break the mismatched neighbour, cascading into a lone half-door that villagers can't open.
        int flag = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(lower, fd.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), flag);
        level.setBlock(lower.above(), fd.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), flag);
    }

    /** A convertible neighbour in the 26-neighbourhood (scan starts at a random offset to avoid bias) that is
     *  also within a brain node's range, or null. */
    /** Turn small plants in the 26-neighbourhood of {@code center} into fertile grass. Called just BEFORE the
     *  block at {@code center} flips to fertile, so a plant standing on it is replaced (and survives on the new
     *  fertile soil) instead of popping off via vanilla canSurvive. Bounded: one 26-cell scan per conversion. */
    private static void convertNearbyPlants(ServerLevel level, InfectionData inf, BlockPos center,
                                            StructureZones zones, MarkerZones marks, String colour) {
        BlockState fg = coloured("fertile_grass", colour,
                com.example.villagefightsback.ModBlocks.FERTILE_GRASS.get()).defaultBlockState();
        BlockState fk = coloured("fertile_kelp", colour,
                com.example.villagefightsback.ModBlocks.FERTILE_KELP.get()).defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int[] o : N26) {
            m.set(center.getX() + o[0], center.getY() + o[1], center.getZ() + o[2]);
            if (!level.isLoaded(m)) continue;
            // This reaches one block PAST the converted cell, so it can poke into a building even when the cell
            // itself is outside one. Guard every write, not just the parent cell.
            if (zones.contains(m.getX(), m.getY(), m.getZ())) continue;
            if (marks.blocked(m.getX(), m.getY(), m.getZ())) continue;   // a player has claimed this ground
            BlockState s = level.getBlockState(m);
            // Each plant JOINS THE FRONTIER. Without that it is an orphan: the engine never walks it, so the
            // ground beneath it is never converted -- which is why a flower turned into fertile grass left
            // ordinary dirt under it while grass the engine converted directly did not. FertileGrassBlock's
            // own comment already spells this rule out; this call site simply was not following it.
            //
            // No incConverted: the tally counts fertilized GROUND, not plants, and the soil below counts
            // itself when the walk reaches it.
            if (s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)) {        // underwater kelp -> our fertile kelp
                BlockPos at = m.immutable();
                level.setBlock(at, fk, 3);
                inf.addFrontier(at.asLong());
            } else if (BlockMatcher.isConvertiblePlant(s)) {           // grass/ferns/flowers -> fertile grass
                BlockPos at = m.immutable();
                level.setBlock(at, fg, 3);
                inf.addFrontier(at.asLong());
            }
        }
    }

    private static BlockPos findCandidate(ServerLevel level, BlockPos pos, RandomSource rnd, double[][] ranges,
                                          StructureZones zones, MarkerZones marks) {
        int start = rnd.nextInt(N26.length);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < N26.length; k++) {
            int[] o = N26[(start + k) % N26.length];
            m.set(pos.getX() + o[0], pos.getY() + o[1], pos.getZ() + o[2]);
            if (!level.isLoaded(m)) continue;
            if (!inRange(m.getX() + 0.5, m.getZ() + 0.5, ranges)) continue; // beyond the brain node reach -> skip
            if (marks.blocked(m.getX(), m.getY(), m.getZ())) continue;      // a player has claimed this ground
            // The village never eats its own buildings. This is THE hot guard -- see StructureZones for why it
            // is a chunk-bucketed lookup and not a scan over every building.
            BlockState ns;
            if (zones.contains(m.getX(), m.getY(), m.getZ())) {
                // Inside a claimed building. Ours and vanilla's blocks stay off limits -- the village does not
                // digest its own houses -- but something a third mod put in there is fair game. The zone test
                // stays FIRST so a world with no buildings still short-circuits on the empty chunk index and
                // never reads a block state it did not read before.
                ns = level.getBlockState(m);
                if (!BlockMatcher.isAlien(ns)) continue;
            } else {
                ns = level.getBlockState(m);
            }
            // Only RAW convertible ground (-> fertile). Already-fertile blocks are NOT re-targeted: rich occurs
            // only via the RICH_CHANCE roll at first conversion, so fertile zones never "ripen" wholesale to rich.
            if (BlockMatcher.isCandidate(level, m, ns)) return m.immutable();
        }
        return null;
    }

    /**
     * Re-add the still-standing infection blocks around {@code pos} to the frontier. Interior cells retire out
     * of the frontier once they're boxed in, so a fully-converted zone goes dormant; when something blows a
     * hole in it, the surviving rim blocks must be woken up to re-convert the freshly-exposed ground. Without
     * this, an explosion in the middle of a large zone is never reclaimed.
     */
    private static void reactivateNeighbours(InfectionData inf, ServerLevel level, BlockPos pos) {
        for (int[] o : N26) {
            BlockPos np = new BlockPos(pos.getX() + o[0], pos.getY() + o[1], pos.getZ() + o[2]);
            if (level.isLoaded(np) && BlockMatcher.isInfection(level.getBlockState(np))) {
                inf.addFrontier(np.asLong());
            }
        }
    }

    // --- miners (spec 4 + spec 6) -------------------------------------------------------------------
    private static void staffMiners(ServerLevel level, BlockPos brainNode) {
        AABB box = new AABB(brainNode).inflate(MINER_AREA);
        List<Villager> near = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby());
        if (near.isEmpty()) return;
        int target = (int) Math.round(MINER_FRACTION * near.size());
        int have = 0;
        for (Villager v : near) if (MinerRole.isMiner(v)) have++;

        if (have < target) {
            int need = target - have;
            for (Villager v : near) {
                if (need <= 0) break;
                if (MinerRole.isMiner(v) || BuilderRole.isBuilder(v) || !MinerRole.eligible(v)) continue;
                MinerRole.promote(v);
                v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE)); // iron tools, instantly
                v.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
                ensureMinerGoal(v);
                need--;
            }
        } else if (have > target) {
            int extra = have - target;
            for (Villager v : near) {
                if (extra <= 0) break;
                if (MinerRole.isMiner(v)) { MinerRole.demote(v); extra--; }
            }
        }
    }

    /** Add the (dormant) MinerGoal to a villager if it doesn't have one yet. */
    public static void ensureMinerGoal(Villager v) {
        com.example.villagefightsback.VillageEvents.enableDoorOpening(v); // a running miner must open wooden doors
        for (WrappedGoal w : v.goalSelector.getAvailableGoals()) {
            if (w.getGoal() instanceof MinerGoal) return;
        }
        v.goalSelector.addGoal(4, new MinerGoal(v));
    }
}
