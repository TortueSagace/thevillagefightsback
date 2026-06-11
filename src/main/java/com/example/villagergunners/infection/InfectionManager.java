package com.example.villagergunners.infection;

import java.util.ArrayList;
import java.util.List;

import com.example.villagergunners.BuilderRole;
import com.example.villagergunners.GravemindData;
import com.example.villagergunners.VillagerGunners;
import com.example.villagergunners.goal.MinerGoal;
import com.example.villagergunners.wall.WallBlock;
import com.example.villagergunners.wall.WallPlan;

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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the gravemind block infection (level 2+). Designed to stay cheap at any zone size:
 *  - Path growth (spec 1) touches only the &le;100 newest path blocks, weighted to extend in a line.
 *  - Conversion (spec 2-3) walks an ACTIVE FRONTIER under a fixed per-tick budget (round-robin), so cost is
 *    bounded by the budget regardless of total converted area; interior blocks retire out of the frontier.
 *  - The same frontier walk doubles as destruction detection: a frontier cell whose block has vanished and
 *    was not a sanctioned removal (player break / miner dig) is treated as an ATTACK -> local speed-up (spec 8).
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectionManager {
    private InfectionManager() {}

    private static final int PROCESS_INTERVAL = 5;   // run path+conversion every 5 ticks (slow, low-lag)
    private static final int MANAGE_INTERVAL = 20;   // seed villages (cheap guard) every 1s
    private static final int STAFF_INTERVAL = 100;   // (re)staff miners every 5s -- the only entity scan, kept sparse
    private static final int START_LEVEL = 2;        // infection begins at gravemind level 2

    private static final double PATH_GROW_CHANCE = 0.60;  // per head per pass -- paths must clearly outrun conversion
    private static final double CONV_CHANCE = 0.15;       // per frontier cell per pass (well below paths so paths lead)
    // Process the WHOLE active frontier each pass (symmetric growth on every edge) up to this hard cap; the
    // frontier converges to the active perimeter (interior cells retire), so this stays cheap until very large
    // zones, where the cap bounds per-tick cost. (The old tiny budget made growth slow + lopsided.)
    private static final int MAX_CELLS_PER_PASS = 8192;
    private static final double SAND_DROP_CHANCE = 0.04;  // a stuck path over air/water drips fertile sand
    private static final float RICH_CHANCE = 0.05F;       // converted block: 5% rich, else fertile
    private static final long FAST_WINDOW = 200L;         // first 10s after a village triggers = 5x
    private static final double FAST_MULT = 5.0;
    private static final long BOOST_DURATION = 600L;      // +100% for 30s near a lost block (spec 8)

    private static final int SEED_RADIUS = 24;       // one-time scan for dirt paths around a new lvl-2 village
    private static final int SEED_Y = 5;
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long tc = server.getTickCount();

        if (tc % MANAGE_INTERVAL == 0) {
            boolean doStaff = (tc % STAFF_INTERVAL == 0); // entity scans only every 5s
            GravemindData gdata = GravemindData.get(server);
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
            for (ServerLevel level : server.getAllLevels()) {
                InfectionData inf = InfectionData.get(level);
                long now = level.getGameTime();
                inf.pruneBoosts(now);
                inf.cleanupSanctioned(now);
                if (inf.frontierSize() == 0) {
                    // still let path heads grow even if the frontier momentarily empty
                }
                double globalMult = inf.isFast(now) ? FAST_MULT : 1.0;
                growPaths(level, inf, now, globalMult);
                convert(level, inf, now, globalMult);
            }
        }
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
        if (BlockMatcher.isRich(s)) inf.removeRich(key);
    }

    /** Explosions: every infection block they destroy is an ATTACK unless a player/villager caused the blast. */
    @SubscribeEvent
    public static void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        net.minecraft.world.entity.Entity cause = event.getExplosion().getExploder();
        boolean friendly = cause instanceof net.minecraft.world.entity.player.Player || cause instanceof Villager;
        InfectionData inf = InfectionData.get(sl);
        long now = sl.getGameTime();
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (!BlockMatcher.isInfection(sl.getBlockState(pos))) continue;
            long key = pos.asLong();
            inf.removeRich(key);
            inf.sanction(key, now);                                   // accounted for -> frontier scan won't re-boost
            if (!friendly) inf.addBoost(key, now + BOOST_DURATION);   // the ground feels attacked (spec 8)
            inf.retire(key);                                          // it's gone -> drop it from the frontier
            reactivateNeighbours(inf, sl, pos);                       // wake the surviving rim to reclaim the crater
        }
    }

    /** A miner that dies frees its slot immediately (the emerald drops via its 1.0 drop chance); the next
     *  staffMiners pass promotes a replacement (spec 6). */
    @SubscribeEvent
    public static void onMinerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager v && MinerRole.isMiner(v)) MinerRole.demote(v);
    }

    /** A non-villager mob destroying an infection block (e.g. griefing) is an ATTACK. */
    @SubscribeEvent
    public static void onMobDestroyBlock(net.minecraftforge.event.entity.living.LivingDestroyBlockEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
        if (!BlockMatcher.isInfection(event.getState())) return;
        InfectionData inf = InfectionData.get(sl);
        long key = event.getPos().asLong();
        long now = sl.getGameTime();
        inf.removeRich(key);
        inf.sanction(key, now);
        if (!(event.getEntity() instanceof Villager)) inf.addBoost(key, now + BOOST_DURATION);
        inf.retire(key);
        reactivateNeighbours(inf, sl, event.getPos());
    }

    // --- seeding (one-time per village when it reaches level 2) -------------------------------------
    private static void seedVillage(ServerLevel level, InfectionData inf, GlobalPos gp, GravemindData data) {
        BlockPos center = gp.pos();
        inf.markSeeded(center.asLong());
        inf.setFastUntil(level.getGameTime() + FAST_WINDOW); // spec 7: 5x for the first 10s

        // Stop building: demote nearby builders (they go idle; staffMiners then makes 20% of them miners).
        for (Villager v : level.getEntitiesOfClass(Villager.class,
                new AABB(center).inflate(96.0), v -> v.isAlive())) {
            BuilderRole.demote(v);
        }

        // The gravemind finishes the wall itself (no builders any more), then lays a dirt-path ring along the
        // wall footprint so the infection escapes the envelope and spreads outward.
        WallPlan plan = data.getWallPlan(gp);
        if (plan != null) {
            for (WallBlock wb : plan.blocks()) {
                BlockPos p = wb.pos();
                if (!level.isLoaded(p)) continue;
                if (!plan.satisfied(level, wb) && level.getBlockState(p).canBeReplaced()) {
                    level.setBlock(p, wb.toState(), 3);                 // finish remaining gaps (no bulldozing)
                }
                if (wb.course() == 0) seedWallPath(level, inf, p, center); // path just outside the foundation
            }
            plan.markComplete();
            com.example.villagergunners.wall.WallBuild.clearPlan(gp);
            data.setDirty();
        }

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

    /** Lay a dirt path at the surface just OUTSIDE a wall foundation column (so it can spread beyond the wall;
     *  a path can't survive directly under the solid wall block, so it sits one step out at ground level). */
    private static void seedWallPath(ServerLevel level, InfectionData inf, BlockPos foundation, BlockPos gravemind) {
        int dx = foundation.getX() - gravemind.getX();
        int dz = foundation.getZ() - gravemind.getZ();
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
            BlockState above = level.getBlockState(g.above());
            if (above.isAir() || !above.getFluidState().isEmpty() || !above.blocksMotion()) {
                level.setBlock(g, Blocks.DIRT_PATH.defaultBlockState(), 3);
                inf.addPathHead(g.asLong());
                return;
            }
        }
    }

    // --- path growth (spec 1 + spec 5) --------------------------------------------------------------
    private static final Direction[] HORIZ = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private static void growPaths(ServerLevel level, InfectionData inf, long now, double globalMult) {
        RandomSource rnd = level.getRandom();
        for (long head : inf.pathHeadsSnapshot()) {
            BlockPos p = BlockPos.of(head);
            if (!level.isLoaded(p)) continue;
            if (!BlockMatcher.isPath(level.getBlockState(p))) { inf.dropPathHead(head); continue; }
            double chance = Math.min(1.0, PATH_GROW_CHANCE * globalMult * inf.multiplierAt(p, now));
            if (rnd.nextDouble() >= chance) continue;
            int res = tryGrow(level, inf, p, rnd);
            if (res < 0) {
                // spec 5: a path with NO viable extension, sitting over air/water, may drip a fertile sand below
                BlockPos below = p.below();
                if (level.isLoaded(below)) {
                    BlockState bs = level.getBlockState(below);
                    boolean voidOrWater = bs.isAir() || !bs.getFluidState().isEmpty();
                    if (voidOrWater && rnd.nextDouble() < SAND_DROP_CHANCE && BlockMatcher.fertileSand() != null) {
                        level.setBlock(below, BlockMatcher.fertileSand().defaultBlockState(), 3);
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
    private static int tryGrow(ServerLevel level, InfectionData inf, BlockPos p, RandomSource rnd) {
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
            targets[i] = extendTarget(level, p, HORIZ[i]);
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

    /** The ground cell (within +/-2 in Y) the path would convert to dirt-path in direction {@code d}, or null. */
    private static BlockPos extendTarget(ServerLevel level, BlockPos p, Direction d) {
        int nx = p.getX() + d.getStepX(), nz = p.getZ() + d.getStepZ();
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos g = new BlockPos(nx, p.getY() + dy, nz);
            if (!level.isLoaded(g)) continue;
            BlockState gs = level.getBlockState(g);
            if (!BlockMatcher.isPathGround(gs)) continue;            // must be dirt-like ground (not already a path)
            BlockState above = level.getBlockState(g.above());
            if (above.isAir() || !above.getFluidState().isEmpty() || !above.blocksMotion()) return g; // walkable surface
        }
        return null;
    }

    // --- conversion (spec 2-3) + destruction detection (spec 8) -------------------------------------
    private static void convert(ServerLevel level, InfectionData inf, long now, double globalMult) {
        // The fast-window/boost multipliers apply to the per-cell CHANCE only (below), not the cell count, so
        // the per-tick cost stays bounded regardless of speed-ups (anti-lag).
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
                inf.retire(key);
                reactivateNeighbours(inf, level, pos); // re-take the exposed ground (heals craters / damage)
                continue;
            }

            // The leading path tips (the <=100 newest path blocks) do NOT fertilize, so the dirt paths keep
            // racing ahead onto fresh ground and conversion fills in BEHIND them (spec 3). Keep them active.
            if (inf.isPathHead(key)) { inf.reactivate(key); continue; }

            BlockPos cand = findCandidate(level, pos, rnd);
            if (cand == null) { inf.retire(key); continue; }           // nothing left to convert here -> drop it

            double chance = Math.min(1.0, CONV_CHANCE * globalMult * inf.multiplierAt(pos, now));
            if (rnd.nextDouble() < chance) {
                boolean richVariant = rnd.nextFloat() < RICH_CHANCE;
                BlockState conv = BlockMatcher.convertedState(level.getBlockState(cand), richVariant);
                if (conv != null) {
                    level.setBlock(cand, conv, 3);
                    inf.addFrontier(cand.asLong());
                    if (richVariant) inf.addRich(cand.asLong());
                }
            }
            inf.reactivate(key); // may still have other candidates next time
        }
    }

    /** A convertible neighbour in the 26-neighbourhood (scan starts at a random offset to avoid bias), or null. */
    private static BlockPos findCandidate(ServerLevel level, BlockPos pos, RandomSource rnd) {
        int start = rnd.nextInt(N26.length);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < N26.length; k++) {
            int[] o = N26[(start + k) % N26.length];
            m.set(pos.getX() + o[0], pos.getY() + o[1], pos.getZ() + o[2]);
            if (!level.isLoaded(m)) continue;
            BlockState ns = level.getBlockState(m);
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
    private static void staffMiners(ServerLevel level, BlockPos gravemind) {
        AABB box = new AABB(gravemind).inflate(MINER_AREA);
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
        for (WrappedGoal w : v.goalSelector.getAvailableGoals()) {
            if (w.getGoal() instanceof MinerGoal) return;
        }
        v.goalSelector.addGoal(4, new MinerGoal(v));
    }
}
