package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
    private static final int BOSS_COST = 600;
    private static final int ELITE_COST = 300;
    private static final double DEFEND_FRAC = 0.75;
    private static final double HARVEST_FRAC = 0.30;
    private static final int MIN_SPAWN_COST = 6;          // cheapest unit (salmon)
    private static final int EXPLORER_TARGET = 3;         // early game: keep this many scouts (cat/mite) roaming
    private static final double SCOUT_FRAC = 0.5;         // exploration spend fraction
    private static final double MARK_MIN_DIST = 24.0;     // only Emerald-Marker prey at least this far from the node
    private static final double MARK_MAX_BELOW = 5.0;     // never scent prey more than this far BELOW the node (skip caves)
    private static final double MARK_MAX_ABOVE = 10.0;    // never scent prey more than this far ABOVE the node
    private static final float MARK_CHANCE = 0.25F;       // chance to even attempt a scent on an eligible decision
    private static final long SCENT_COOLDOWN = 20L * 60L * 4L; // a brain may place a scent at most every 4 minutes
    private static final Map<GlobalPos, Long> LAST_SCENT = new HashMap<>(); // per-brain last-scent tick (getTickCount)
    private static final double UNDERGROUND_BELOW = 8.0;  // ignore prey + spawn anchors this far below the node (caves)
    private static final String LAST_DMG_TAG = "OrganicLastDmg"; // game-time a unit last dealt damage
    private static final long IDLE_RECLAIM_TICKS = 6000L; // 5 in-game minutes dealing no damage -> reclaim the unit
    private static final float RECLAIM_MAX_HP = 100.0F;   // only units below this max HP are auto-reclaimed
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
        int C = fieldCap();
        BlockPos node = gp.pos();
        long gameTime = level.getGameTime();

        // ONE entity scan, partitioned: field size F, explorer count, prey (passive vs hostile) -- and reclaim any
        // idle, stuck unit (refunding its organic mass) so the field self-heals (e.g. units stranded in a hole).
        AABB box = new AABB(node).inflate(BrainNodeEvents.INCOME_RADIUS);
        List<LivingEntity> prey = new ArrayList<>();
        int passive = 0, hostile = 0, field = 0, explorers = 0, reclaimed = 0, reclaimedMass = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (Factions.isEmerald(e)) {
                if (e instanceof Mob m) {
                    int back = reclaimIfIdle(econ, m, gameTime);
                    if (back > 0) { reclaimed++; reclaimedMass += back; continue; }
                }
                field++;
                if (e instanceof EmeraldCat || e instanceof EmeraldSalmon) explorers++; // roaming scouts: land cats + water salmon (the screen we maintain)
            } else if (EmeraldMobs.isHostileTarget(e)) {
                // Ignore prey the ground swarm can't reach: deep in caves (more than UNDERGROUND_BELOW under the
                // node) or flying. This keeps the brain hunting the threat OUTSIDE the village, not chasing cave mobs.
                if (e.getY() < node.getY() - UNDERGROUND_BELOW || isFlyer(e)) continue;
                if (prey.size() < PREY_SCAN_CAP) prey.add(e);
                if (e instanceof Enemy) hostile++; else passive++;
            }
        }
        if (reclaimed > 0) log(level, gp, "Reclaimed " + reclaimed + " idle unit" + (reclaimed > 1 ? "s" : "")
                + " (+" + reclaimedMass + ")");

        int P = econ.getOrganicPoints(); // read AFTER reclaim, which refunds points
        if (P - BrainNodeData.ORGANIC_FLOOR < MIN_SPAWN_COST) return; // even on credit nothing is affordable -> BANK

        double threat = readThreat(gp, now);

        // Rally: paint a DISTANT prey with the Emerald Marker so idle units converge on prey they'd never find on
        // their own (used sparingly -- at most EmeraldMarker.MAX_ACTIVE live at once). Runs regardless of the spawn
        // decision below.
        if (field > 0 && !prey.isEmpty() && level.getRandom().nextFloat() < MARK_CHANCE
                && now - LAST_SCENT.getOrDefault(gp, now - SCENT_COOLDOWN) >= SCENT_COOLDOWN  // 4-minute per-brain cooldown (first mark allowed immediately; Long.MIN_VALUE default would overflow and block forever)
                && EmeraldMarker.activeCount(level) < EmeraldMarker.MAX_ACTIVE) {
            LivingEntity far = null;
            double bestD = Double.MAX_VALUE;
            double minD2 = MARK_MIN_DIST * MARK_MIN_DIST;
            double minY = node.getY() - MARK_MAX_BELOW;
            double maxY = node.getY() + MARK_MAX_ABOVE;
            for (LivingEntity p : prey) {
                if (EmeraldMarker.isMarked(p)) continue;
                // scent only prey within 5 below / 10 above the node (the prey list already dropped caves/flyers).
                if (p.getY() < minY || p.getY() > maxY) continue;
                double d = p.blockPosition().distSqr(node);
                if (d >= minD2 && d < bestD) { bestD = d; far = p; } // nearest prey beyond MARK_MIN_DIST
            }
            if (far != null) {
                EmeraldMarker.mark(level, far);
                LAST_SCENT.put(gp, now);
                String name = far.getName().getString();
                if (name.length() > 20) name = name.substring(0, 20) + "..."; // trim long names (custom/player names)
                int dist = (int) Math.round(Math.sqrt(bestD));
                log(level, gp, "Marked a scent on " + name + ", " + dist + " blocks away");
            }
        }

        if (field >= C) return; // army full -> BANK (points keep accruing for a later burst)

        // Spawn anchors: infested blocks within radius + the brain node itself (always valid). Drop deep-underground
        // anchors so units never spawn stranded in a cave; the node is always a valid surface anchor.
        List<BlockPos> anchors = InfectionManager.infestedPathsWithin(level, node, BrainNodeEvents.INCOME_RADIUS);
        // Drop deep-underground anchors so LAND units never spawn stranded in a cave -- but KEEP deep WATER anchors:
        // a submerged infested path is the natural home of an aquatic unit, not a cave trap, so a single infested
        // path inside the water always enables water units regardless of how far below the node it sits.
        anchors.removeIf(a -> a.getY() < node.getY() - UNDERGROUND_BELOW && !waterAdjacent(level, a));
        anchors.add(node);
        List<BlockPos> waterAnchors = new ArrayList<>();
        for (BlockPos a : anchors) if (waterAdjacent(level, a)) waterAnchors.add(a);

        int spendable = P - BrainNodeData.ORGANIC_FLOOR; // = P + 32: includes the deploy-on-credit buffer
        double mult = Config.organicSpawnBudgetMultiplier();

        // ---- the decision tree: pick a leaf, a per-decision budget, and a spawn count ----
        int leaf, budget, maxSpawns;
        if (threat >= DEFEND_THRESHOLD) {
            if (threat >= BOSS_THREAT && P >= BOSS_COST) { leaf = BOSS; budget = spendable; maxSpawns = 1; }
            else if (P >= ELITE_COST) { leaf = ELITE; budget = budget(spendable, DEFEND_FRAC, mult); maxSpawns = K; }
            else { leaf = SWARM_COMBAT; budget = budget(spendable, DEFEND_FRAC, mult); maxSpawns = K; }
        } else if (explorers < EXPLORER_TARGET && P > 0) {
            // Keep a roaming screen of explorer cats out at all times (to find distant prey), as soon as the brain
            // has spare income -- positive points, so the early bootstrap credit still goes to hunters first. Cats
            // are never reclaimed, so this is a one-time investment per lost cat, not a drain.
            leaf = SCOUT;
            budget = budget(spendable, SCOUT_FRAC, mult);
            maxSpawns = Math.min(K, EXPLORER_TARGET - explorers);
        } else if (!prey.isEmpty()) {
            leaf = (passive >= hostile) ? CHEAP_CONVERTER : HUNTER;
            budget = budget(spendable, HARVEST_FRAC, mult);
            maxSpawns = K;
        } else if (explorers < EXPLORER_TARGET) {
            // No prey detected at all -> explore to find some (even on credit).
            leaf = SCOUT;
            budget = budget(spendable, SCOUT_FRAC, mult);
            maxSpawns = Math.min(K, EXPLORER_TARGET - explorers);
        } else {
            return; // enough scouts out, no prey, no threat -> BANK (points keep accruing).
        }

        String summary = deploy(level, econ, leaf, budget, maxSpawns, prey, anchors, waterAnchors, node, field, C);
        if (!summary.isEmpty()) {
            LimboBanishFx.startLimeAt(level, node.getX() + 0.5, node.getY(), node.getZ() + 0.5); // the brain itself pulses green when it deploys
            log(level, gp, leafName(leaf) + ": " + summary);
        }
    }

    private static int budget(int base, double frac, double mult) {
        return Math.max(MIN_SPAWN_COST, (int) Math.round(base * frac * mult));
    }

    /** Reclaim a small, idle, STUCK emerald unit: if {@code m}'s max HP is below {@link #RECLAIM_MAX_HP} and it has
     *  dealt no damage for {@link #IDLE_RECLAIM_TICKS} (5 min), refund its organic mass to {@code econ} and despawn
     *  it (frees units stranded in a hole). Returns the refunded points, or 0 if it was NOT reclaimed.
     *  Explorers/scouts (cats, water salmon, mites) are EXEMPT -- roaming without fighting is their whole job, so they
     *  must never be culled for being "idle" (and mites self-despawn after ~1 min anyway). */
    private static int reclaimIfIdle(BrainNodeData.Econ econ, Mob m, long gameTime) {
        if (m instanceof EmeraldCat || m instanceof EmeraldSalmon || m instanceof EmeraldEndermite) return 0; // scouts never auto-culled
        if (m instanceof net.minecraft.world.entity.animal.IronGolem) return 0; // emerald golems (+ both variants) exempt
        if (m.getMaxHealth() >= RECLAIM_MAX_HP) return 0; // other heavy units (elites / withers) are never auto-culled
        net.minecraft.nbt.CompoundTag pd = m.getPersistentData();
        if (!pd.contains(LAST_DMG_TAG)) { pd.putLong(LAST_DMG_TAG, gameTime); return 0; } // start its idle clock
        if (gameTime - pd.getLong(LAST_DMG_TAG) < IDLE_RECLAIM_TICKS) return 0;
        int refund = Math.round(m.getMaxHealth());
        econ.addOrganicPoints(refund); // give the mass back to the brain
        LimboBanishFx.startLime(m);
        m.discard();
        return refund;
    }

    /** Record that an emerald unit just dealt damage (resets its idle-reclaim clock). Called from the damage hook. */
    public static void noteDamageDealt(LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel sl) {
            attacker.getPersistentData().putLong(LAST_DMG_TAG, sl.getGameTime());
        }
    }

    /** A flying creature -- never scented (the ground swarm can't chase it). Covers ghast/phantom, bee/parrot,
     *  bats, and anything else moving gravity-free. */
    private static boolean isFlyer(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.FlyingMob
                || e instanceof net.minecraft.world.entity.animal.FlyingAnimal
                || e instanceof net.minecraft.world.entity.ambient.AmbientCreature
                || e.isNoGravity();
    }

    private static int fieldCap() {
        int base = Config.emeraldMobsMaxNumber();
        if (base <= 0) return Integer.MAX_VALUE; // 0 = unlimited
        return Math.max(1, (int) Math.round(base * Config.organicArmyMultiplier()));
    }

    // ============================ spend loop + WHERE ============================

    private static String deploy(ServerLevel level, BrainNodeData.Econ econ, int leaf, int budget, int maxSpawns,
                                 List<LivingEntity> prey, List<BlockPos> anchors, List<BlockPos> waterAnchors,
                                 BlockPos node, int field, int cap) {
        List<Cand> pool = candidates(leaf);
        java.util.LinkedHashMap<EntityType<?>, Integer> spawned = new java.util.LinkedHashMap<>();
        int n = 0, targetIdx = 0;
        while (n < maxSpawns && field + n < cap) {
            // affordable budget includes the deploy-on-credit buffer down to ORGANIC_FLOOR
            int remaining = Math.min(budget, econ.getOrganicPoints() - BrainNodeData.ORGANIC_FLOOR);
            Cand pick = weightedPick(pool, remaining, !waterAnchors.isEmpty());
            if (pick == null) break; // nothing affordable / valid
            BlockPos target = prey.isEmpty() ? node : prey.get(targetIdx++ % prey.size()).blockPosition();
            BlockPos anchor = nearestAnchor(pick.aquatic ? waterAnchors : anchors, target);
            if (anchor == null) break;
            spawn(level, pick.type, anchor);
            econ.spendOrganicPoints(pick.cost);
            budget -= pick.cost;
            spawned.merge(pick.type, 1, Integer::sum);
            n++;
        }
        if (spawned.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<EntityType<?>, Integer> e : spawned.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append("x ").append(shortName(e.getKey()));
        }
        return sb.toString();
    }

    // --- per-brain organic decision log (transient; newest last), shown in the UI's Organic log tab ---
    private static final Map<GlobalPos, java.util.ArrayDeque<String>> LOG = new HashMap<>();

    public static List<String> logFor(GlobalPos node) {
        java.util.ArrayDeque<String> q = LOG.get(node);
        return q == null ? List.of() : new ArrayList<>(q);
    }

    private static void log(ServerLevel level, GlobalPos gp, String msg) {
        java.util.ArrayDeque<String> q = LOG.computeIfAbsent(gp, k -> new java.util.ArrayDeque<>());
        long day = level.getServer().overworld().getDayTime() / 24000L;
        q.addLast("Day " + day + ": " + msg);
        while (q.size() > 10) q.removeFirst();
    }

    private static String leafName(int leaf) {
        switch (leaf) {
            case BOSS: return "BOSS";
            case ELITE: return "ELITE";
            case SWARM_COMBAT: return "SWARM";
            case HUNTER: return "HUNT";
            case CHEAP_CONVERTER: return "HARVEST";
            case SCOUT: return "SCOUT";
            default: return "?";
        }
    }

    private static String shortName(EntityType<?> type) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return "?";
        String p = id.getPath();
        if (p.startsWith("emerald_")) return p.substring("emerald_".length());
        if (p.equals("rich_sheep")) return "sheep";
        return p;
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
        if (!Config.emeraldMobsEnabled()) return; // emerald faction disabled -> never deploy an emerald unit
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) return;
        BlockPos at = anchor.above();
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.getPersistentData().putBoolean(EmeraldPopulation.UNCAPPED_TAG, true);
        mob.getPersistentData().putLong(LAST_DMG_TAG, level.getGameTime()); // start its idle-reclaim clock at spawn
        // Emerald golems (iron + defender + gigachad variants) are a heavy investment: make them persistent so they
        // never vanilla-despawn when the player travels away (they are also exempt from the idle-reclaim cull).
        if (mob instanceof net.minecraft.world.entity.animal.IronGolem) mob.setPersistenceRequired();
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        level.addFreshEntity(mob);
        LimboBanishFx.startLime(mob); // the lime emergence swirl (the conversion/limbo-throw animation) on the new unit
        if (type == ModEntities.EMERALD_WITHER.get()) { // everyone nearby "witnesses the birth of an emerald wither"
            Achievements.awardNearby(level, mob.getX(), mob.getY(), mob.getZ(), 64.0, "brain_node/im_not_kidding");
        }
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
            // SCOUT: a cat (land explorer) to find new prey; a salmon (water explorer) to sweep nearby water when
            // there's water to deploy into; mite as a cheap roaming combat scout.
            leaves[SCOUT] = List.of(
                    new Cand(ModEntities.EMERALD_CAT.get(), 20, 8.0, false),        // the roaming LAND explorer
                    new Cand(ModEntities.EMERALD_SALMON.get(), 6, 4.0, true),       // the roaming WATER explorer (aquatic)
                    new Cand(ModEntities.EMERALD_ENDERMITE.get(), 16, 4.0, false)); // cheap roaming combat scout
                    // (bat dropped: it can't roam-explore -- it just flutters. It still appears as a CHEAP_CONVERTER.)
            // CHEAP_CONVERTER: harvest passive food cheaply. Mite dominates; animals are deliberately rare.
            leaves[CHEAP_CONVERTER] = List.of(
                    new Cand(ModEntities.EMERALD_ENDERMITE.get(), 16, 5.0, false),
                    new Cand(ModEntities.EMERALD_BAT.get(), 12, 1.0, false),
                    new Cand(ModEntities.EMERALD_WOLF.get(), 16, 0.8, false),
                    new Cand(ModEntities.EMERALD_CHICKEN.get(), 8, 0.6, false),
                    new Cand(ModEntities.EMERALD_COW.get(), 20, 0.6, false),
                    new Cand(ModEntities.EMERALD_PIG.get(), 20, 0.6, false),
                    new Cand(ModEntities.RICH_SHEEP.get(), 16, 0.5, false),
                    new Cand(ModEntities.EMERALD_SALMON.get(), 6, 1.5, true));
            // HUNTER: mid-cost combat to chase down hostiles; cheap zombie tanks + ranged skeletons lead.
            leaves[HUNTER] = List.of(
                    new Cand(ModEntities.EMERALD_ZOMBIE.get(), 20, 5.0, false),
                    new Cand(ModEntities.EMERALD_SKELETON.get(), 40, 5.0, false),
                    new Cand(ModEntities.EMERALD_PHANTOM.get(), 40, 3.0, false),
                    new Cand(ModEntities.EMERALD_WOLF.get(), 16, 0.8, false),
                    new Cand(ModEntities.EMERALD_HORSE.get(), 30, 0.8, false),
                    new Cand(ModEntities.EMERALD_GUARDIAN.get(), 60, 2.0, true));
            // SWARM_COMBAT: many cheap combat units under attack. Zombie (20) is the cheap tank, skeleton the ranged core.
            leaves[SWARM_COMBAT] = List.of(
                    new Cand(ModEntities.EMERALD_ZOMBIE.get(), 20, 5.0, false),
                    new Cand(ModEntities.EMERALD_SKELETON.get(), 40, 5.0, false),
                    new Cand(ModEntities.EMERALD_CREEPER.get(), 40, 4.0, false),
                    new Cand(ModEntities.EMERALD_PHANTOM.get(), 40, 3.0, false));
            // ELITE: a few expensive tanks/bruisers when the bank is fat.
            leaves[ELITE] = List.of(
                    new Cand(ModEntities.EMERALD_ENDERMAN.get(), 80, 3.0, false),
                    new Cand(ModEntities.EMERALD_IRON_GOLEM.get(), 200, 3.0, false),
                    new Cand(ModEntities.EMERALD_DEFENDER_GOLEM.get(), 300, 2.5, false),
                    new Cand(ModEntities.EMERALD_GIGACHAD_GOLEM.get(), 400, 2.0, false),
                    new Cand(ModEntities.EMERALD_ELDER_GUARDIAN.get(), 160, 1.5, true));
            // BOSS: the saved-up haymaker.
            leaves[BOSS] = List.of(
                    new Cand(ModEntities.EMERALD_WITHER.get(), 600, 6.0, false),
                    new Cand(ModEntities.EMERALD_GIGACHAD_GOLEM.get(), 400, 2.0, false));
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
