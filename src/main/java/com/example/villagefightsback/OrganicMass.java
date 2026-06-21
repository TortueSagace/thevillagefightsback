package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import com.example.villagefightsback.infection.InfectionManager;

/**
 * ORGANIC MASS system (v1.1.2, config-gated by {@link Config#organicMassEnabled()}).
 *
 * <p>When enabled, an emerald unit's kills don't convert the victim; instead the victim's max HP is banked as
 * "organic mass" on the nearest brain node (see {@link EmeraldConversion}). This class is the brain's commander:
 * every few seconds each brain runs a DECISION TREE that decides WHEN to act vs. bank, and WHAT to deploy, then
 * spends organic points to spawn emerald units at infested-path blocks (WHERE = cheap nearest-anchor pick).
 *
 * <ul>
 *   <li><b>When/What (decision tree):</b> {@link #decide} routes to a leaf (BANK / DEFEND -> boss|elite|swarm /
 *       HARVEST -> converter|hunter / SCOUT) based on threat, prey, banked points and field size.</li>
 *   <li><b>Variety:</b> each leaf draws weighted-random from an explicit candidate list (animals weighted low,
 *       humanoid/monster high) so every unit keeps a chance while the useful one dominates.</li>
 *   <li><b>Where:</b> {@link #nearestAnchor} picks the Euclidean-nearest infested block (or the node itself).</li>
 *   <li><b>Threat:</b> a transient, lazily-decaying per-brain counter bumped when an emerald unit dies nearby.</li>
 * </ul>
 * Organic points are a SEPARATE currency from emeralds/level. Spawns are bounded by a per-brain field cap
 * (= emerald_mobs_max_number x organic_army_multiplier).
 */
public final class OrganicMass {
    private OrganicMass() {}

    // --- tuning ---
    private static final int K = 4;                       // max spawns per decision (anti-lag)
    private static final int PREY_SCAN_CAP = 16;
    private static final double DEFEND_THRESHOLD = 2.0;   // threat >= this -> DEFEND
    private static final double BOSS_THREAT = 6.0;        // threat >= this (+ rich bank) -> BOSS
    private static final int BOSS_COST = 300;
    private static final int ELITE_COST = 150;
    private static final double DEFEND_FRAC = 0.75;
    private static final double HARVEST_FRAC = 0.30;
    private static final int MIN_SPAWN_COST = 3;          // cheapest unit (salmon)
    private static final float THREAT_BUMP = 1.0F;
    private static final float THREAT_MAX = 8.0F;
    private static final double THREAT_HALFLIFE = 1200.0; // 60s

    // Transient per-brain threat: [value, lastTick]. Not persisted (resets to calm on restart).
    private static final Map<GlobalPos, double[]> THREAT = new HashMap<>();

    // ============================ hooks called from EmeraldConversion ============================

    /** Bank {@code amount} organic points on the SINGLE nearest brain within INCOME_RADIUS of {@code pos}. */
    static void addToNearestBrain(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0) return;
        GlobalPos gp = nearestBrain(level, pos);
        if (gp != null) BrainNodeData.get(level.getServer()).econ(gp).addOrganicPoints(amount);
    }

    /** An emerald unit died near a brain -> the brain is losing a fight: raise that brain's threat level. */
    static void bumpThreatNearest(ServerLevel level, BlockPos pos) {
        GlobalPos gp = nearestBrain(level, pos);
        if (gp == null) return;
        long now = level.getServer().getTickCount();
        double cur = readThreat(gp, now);
        THREAT.put(gp, new double[]{ Math.min(THREAT_MAX, cur + THREAT_BUMP), now });
    }

    private static GlobalPos nearestBrain(ServerLevel level, BlockPos pos) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        long r2 = (long) BrainNodeEvents.INCOME_RADIUS * BrainNodeEvents.INCOME_RADIUS;
        GlobalPos best = null;
        double bestD = Double.MAX_VALUE;
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (!gp.dimension().equals(level.dimension())) continue;
            double d = gp.pos().distSqr(pos);
            if (d <= r2 && d < bestD) { bestD = d; best = gp; }
        }
        return best;
    }

    private static double readThreat(GlobalPos gp, long now) {
        double[] t = THREAT.get(gp);
        if (t == null) return 0.0;
        return t[0] * Math.pow(2.0, -((now - t[1]) / THREAT_HALFLIFE));
    }

    // ============================ driver ============================

    /** Called every driver pass (40 ticks) from {@link BrainNodeEvents#onServerTick}. Staggers brains across
     *  3 passes so each decides ~every 120 ticks (~6s) and at most a third scan on any one pass. */
    public static void tick(MinecraftServer server, BrainNodeData data) {
        if (!Config.organicMassEnabled()) return;
        long now = server.getTickCount();
        long beat = now / 40L; // driver passes only fire on multiples of 40
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (Math.floorMod(beat + Math.floorMod(gp.hashCode(), 3), 3) != 0) continue; // 1/3 stagger
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            decide(level, data, gp, now);
        }
    }

    // ============================ the decision tree (WHEN + WHAT) ============================

    private static void decide(ServerLevel level, BrainNodeData data, GlobalPos gp, long now) {
        BrainNodeData.Econ econ = data.econ(gp);
        int P = econ.getOrganicPoints();
        if (P < MIN_SPAWN_COST) return; // nothing affordable -> implicit BANK

        int C = fieldCap();
        BlockPos node = gp.pos();

        // ONE entity scan, partitioned: field size F, explorer-out, and prey (passive vs hostile).
        AABB box = new AABB(node).inflate(BrainNodeEvents.INCOME_RADIUS);
        List<LivingEntity> prey = new ArrayList<>();
        int passive = 0, hostile = 0, field = 0;
        boolean explorerOut = false;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (Factions.isEmerald(e)) {
                field++;
                if (e instanceof EmeraldCat) explorerOut = true;
            } else if (EmeraldMobs.isHostileTarget(e)) {
                if (prey.size() < PREY_SCAN_CAP) prey.add(e);
                if (e instanceof Enemy) hostile++; else passive++;
            }
        }
        if (field >= C) return; // army full -> BANK (points keep accruing for a later burst)

        double threat = readThreat(gp, now);

        // Spawn anchors: infested blocks within radius + the brain node itself (always valid).
        List<BlockPos> anchors = InfectionManager.infestedPathsWithin(level, node, BrainNodeEvents.INCOME_RADIUS);
        anchors.add(node);
        List<BlockPos> waterAnchors = new ArrayList<>();
        for (BlockPos a : anchors) if (waterAdjacent(level, a)) waterAnchors.add(a);

        double budgetMult = Config.organicSpawnBudgetMultiplier();

        // ---- the tree ----
        if (threat >= DEFEND_THRESHOLD) {
            if (threat >= BOSS_THREAT && P >= BOSS_COST) {
                deploy(level, econ, BOSS, P, 1, prey, anchors, waterAnchors, node, field, C);
            } else if (P >= ELITE_COST) {
                deploy(level, econ, ELITE, budget(P, DEFEND_FRAC, budgetMult), K, prey, anchors, waterAnchors, node, field, C);
            } else {
                deploy(level, econ, SWARM_COMBAT, budget(P, DEFEND_FRAC, budgetMult), K, prey, anchors, waterAnchors, node, field, C);
            }
        } else if (!prey.isEmpty()) {
            int leaf = (passive >= hostile) ? CHEAP_CONVERTER : HUNTER;
            deploy(level, econ, leaf, budget(P, HARVEST_FRAC, budgetMult), K, prey, anchors, waterAnchors, node, field, C);
        } else if (!explorerOut) {
            deploy(level, econ, SCOUT, P, 1, prey, anchors, waterAnchors, node, field, C); // one explorer to find food
        }
        // else: idle with a scout already out -> BANK.
    }

    private static int budget(int P, double frac, double mult) {
        return Math.max(MIN_SPAWN_COST, (int) Math.round(P * frac * mult));
    }

    private static int fieldCap() {
        int base = Config.emeraldMobsMaxNumber();
        if (base <= 0) return Integer.MAX_VALUE; // 0 = unlimited
        return Math.max(1, (int) Math.round(base * Config.organicArmyMultiplier()));
    }

    // ============================ spend loop + WHERE ============================

    private static void deploy(ServerLevel level, BrainNodeData.Econ econ, int leaf, int budget, int maxSpawns,
                               List<LivingEntity> prey, List<BlockPos> anchors, List<BlockPos> waterAnchors,
                               BlockPos node, int field, int cap) {
        List<Cand> pool = candidates(leaf);
        int spawned = 0;
        int targetIdx = 0;
        while (spawned < maxSpawns && field + spawned < cap) {
            int remaining = Math.min(budget, econ.getOrganicPoints());
            Cand pick = weightedPick(pool, remaining, !waterAnchors.isEmpty());
            if (pick == null) break; // nothing affordable / valid
            BlockPos target = prey.isEmpty() ? node : prey.get(targetIdx++ % prey.size()).blockPosition();
            BlockPos anchor = nearestAnchor(pick.aquatic ? waterAnchors : anchors, target);
            if (anchor == null) break;
            spawn(level, pick.type, anchor);
            econ.spendOrganicPoints(pick.cost);
            budget -= pick.cost;
            spawned++;
        }
    }

    /** WHERE: the Euclidean-nearest anchor to {@code to} (linear scan over <=65 candidates; the unit's own AI paths). */
    private static BlockPos nearestAnchor(List<BlockPos> anchors, BlockPos to) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos a : anchors) {
            double d = a.distSqr(to);
            if (d < bestD) { bestD = d; best = a; }
        }
        return best;
    }

    private static boolean waterAdjacent(ServerLevel level, BlockPos a) {
        if (level.getBlockState(a).getFluidState().isSource()) return true;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (!level.getBlockState(a.relative(dir)).getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** Spawn an emerald unit just above {@code anchor}, bypassing the lag cap (the field cap governs us instead),
     *  with a lime emergence particle burst. */
    private static void spawn(ServerLevel level, EntityType<?> type, BlockPos anchor) {
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) return;
        BlockPos at = anchor.above();
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.getPersistentData().putBoolean(EmeraldPopulation.UNCAPPED_TAG, true);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(mob);
        double x = at.getX() + 0.5, y = at.getY() + 0.4, z = at.getZ() + 0.5;
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.4, 0.6, 0.4, 0.0);
        level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.40F, 1.0F, 0.25F), 1.4F),
                x, y, z, 18, 0.45, 0.7, 0.45, 0.0);
    }

    // ============================ catalog + leaves (WHAT + variety) ============================

    private static final int SCOUT = 0, CHEAP_CONVERTER = 1, HUNTER = 2, SWARM_COMBAT = 3, ELITE = 4, BOSS = 5;

    /** A spawnable candidate: emerald type, organic cost, draw weight in this leaf, and whether it needs water. */
    private record Cand(EntityType<?> type, int cost, double weight, boolean aquatic) {}

    private static List<Cand>[] leaves; // lazily built after entity registration

    @SuppressWarnings("unchecked")
    private static List<Cand> candidates(int leaf) {
        if (leaves == null) {
            leaves = new List[6];
            // SCOUT: a cat (explorer) to find new prey; bat as a cheap flying fallback.
            leaves[SCOUT] = List.of(
                    new Cand(ModEntities.EMERALD_CAT.get(), 10, 8.0, false),
                    new Cand(ModEntities.EMERALD_BAT.get(), 6, 1.5, false));
            // CHEAP_CONVERTER: harvest passive food cheaply. Mite dominates; animals are deliberately rare.
            leaves[CHEAP_CONVERTER] = List.of(
                    new Cand(ModEntities.EMERALD_ENDERMITE.get(), 8, 5.0, false),
                    new Cand(ModEntities.EMERALD_BAT.get(), 6, 1.0, false),
                    new Cand(ModEntities.EMERALD_WOLF.get(), 8, 0.8, false),
                    new Cand(ModEntities.EMERALD_CHICKEN.get(), 4, 0.6, false),
                    new Cand(ModEntities.EMERALD_COW.get(), 10, 0.6, false),
                    new Cand(ModEntities.EMERALD_PIG.get(), 10, 0.6, false),
                    new Cand(ModEntities.RICH_SHEEP.get(), 8, 0.5, false),
                    new Cand(ModEntities.EMERALD_SALMON.get(), 3, 1.5, true));
            // HUNTER: mid-cost combat to chase down hostiles; cheap zombie tanks + ranged skeletons lead.
            leaves[HUNTER] = List.of(
                    new Cand(ModEntities.EMERALD_ZOMBIE.get(), 10, 5.0, false),
                    new Cand(ModEntities.EMERALD_SKELETON.get(), 20, 5.0, false),
                    new Cand(ModEntities.EMERALD_PHANTOM.get(), 20, 3.0, false),
                    new Cand(ModEntities.EMERALD_WOLF.get(), 8, 0.8, false),
                    new Cand(ModEntities.EMERALD_HORSE.get(), 15, 0.8, false),
                    new Cand(ModEntities.EMERALD_GUARDIAN.get(), 30, 2.0, true));
            // SWARM_COMBAT: many cheap combat units under attack. Zombie (10) is the cheap tank, skeleton the ranged core.
            leaves[SWARM_COMBAT] = List.of(
                    new Cand(ModEntities.EMERALD_ZOMBIE.get(), 10, 5.0, false),
                    new Cand(ModEntities.EMERALD_SKELETON.get(), 20, 5.0, false),
                    new Cand(ModEntities.EMERALD_CREEPER.get(), 20, 4.0, false),
                    new Cand(ModEntities.EMERALD_PHANTOM.get(), 20, 3.0, false));
            // ELITE: a few expensive tanks/bruisers when the bank is fat.
            leaves[ELITE] = List.of(
                    new Cand(ModEntities.EMERALD_ENDERMAN.get(), 40, 3.0, false),
                    new Cand(ModEntities.EMERALD_IRON_GOLEM.get(), 100, 3.0, false),
                    new Cand(ModEntities.EMERALD_DEFENDER_GOLEM.get(), 150, 2.5, false),
                    new Cand(ModEntities.EMERALD_GIGACHAD_GOLEM.get(), 200, 2.0, false),
                    new Cand(ModEntities.EMERALD_ELDER_GUARDIAN.get(), 80, 1.5, true));
            // BOSS: the saved-up haymaker.
            leaves[BOSS] = List.of(
                    new Cand(ModEntities.EMERALD_WITHER.get(), 300, 6.0, false),
                    new Cand(ModEntities.EMERALD_GIGACHAD_GOLEM.get(), 200, 2.0, false));
        }
        return leaves[leaf];
    }

    /** Weighted-random over the affordable + environment-valid candidates (null if none). */
    private static Cand weightedPick(List<Cand> pool, int budget, boolean waterAvailable) {
        double total = 0.0;
        for (Cand c : pool) if (c.cost <= budget && (!c.aquatic || waterAvailable)) total += c.weight;
        if (total <= 0.0) return null;
        double r = Math.random() * total;
        for (Cand c : pool) {
            if (c.cost > budget || (c.aquatic && !waterAvailable)) continue;
            r -= c.weight;
            if (r <= 0.0) return c;
        }
        return null;
    }
}
