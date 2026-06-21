package com.example.villagefightsback;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.villagefightsback.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The single, world-global Village Brain Node state, shared across ALL villages: the current level,
 * emeralds accumulated toward the next level, the day the sunset income last ran, and the set of
 * placed brainNode block positions (so income/visuals can find them). Stored on the overworld's data
 * storage, so it persists with the save and is the same no matter which brainNode you interact with.
 */
public class BrainNodeData extends SavedData {
    private static final String NAME = "thevillagefightsback_brain_node";

    public static final int MAX_LEVEL = 4;
    /** Emeralds may go this far into the red, but ONLY at level 1 (the village's max debt / last stand). */
    public static final int MIN_EMERALDS_LEVEL1 = -32;
    /** Organic mass may dip this far below zero (a little deploy-on-credit buffer, like the emerald floor). */
    public static final int ORGANIC_FLOOR = -32;
    // costForNext(level) = emeralds needed to advance FROM that level. (Tunable.)
    private static final int[] COST_TO_ADVANCE = {0, 32, 256, 512}; // 1->2=32, 2->3=256, 3->4=512 (doubled)

    /** The cost to advance from {@code level}, scaled by multiplier_level_up_emerald_threshold (min 1). */
    private static int scaledCost(int level) {
        return Math.max(1, (int) Math.round(COST_TO_ADVANCE[level] * Config.levelUpThresholdMult()));
    }

    // Per-village economy: brainNodes within CLUSTER blocks share one Econ; brainNodes farther apart are
    // completely independent villages, each with its own level/emeralds/income.
    private static final double CLUSTER = 100.0;

    private final Map<GlobalPos, Econ> econs = new HashMap<>();   // one economy per village cluster
    private final Set<GlobalPos> blocks = new HashSet<>();        // placed brainNode blocks
    private final Set<GlobalPos> nodes = new HashSet<>();         // placed node blocks (golem-factory/barracks/lure-tower): +10% sunset gems each, in radius
    private final Set<GlobalPos> servicedCenters = new HashSet<>(); // village centers already given a brainNode
    private final Set<GlobalPos> announced = new HashSet<>();       // brain nodes whose discovery banner already fired (to any player)
    private final Map<GlobalPos, WallPlan> wallPlans = new HashMap<>(); // computed defensive wall per brainNode
    private final Map<GlobalPos, WallPlan> outerWallPlans = new HashMap<>(); // stage-3 OUTER obsidian wall per brainNode
    private final Set<GlobalPos> defendersBuilt = new HashSet<>();  // brainNodes whose level-2 gate turrets are placed
    private final Set<GlobalPos> outerDefendersBuilt = new HashSet<>(); // brainNodes whose OUTER-wall turrets are placed
    private final Set<GlobalPos> domeBuilt = new HashSet<>();       // brainNodes whose level-4 glass dome is placed
    private final Set<GlobalPos> expanded = new HashSet<>();        // brainNodes that have run their stage-3 expansion
    private final Map<GlobalPos, java.util.UUID> bellPlacers = new HashMap<>(); // bell pos -> player who placed it

    public BrainNodeData() {}

    public static BrainNodeData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(BrainNodeData::load, BrainNodeData::new, NAME);
    }

    // --- per-village level / emerald economy ---

    /** The level/emerald economy of one village. */
    public final class Econ {
        private int level = 1;
        private int emeralds = 0;
        private long lastIncomeDay = Long.MIN_VALUE;
        private int organicPoints = 0; // ORGANIC MASS: a SEPARATE spawn-only currency (never touches level/emeralds)

        public int getLevel() { return level; }
        public int getEmeralds() { return emeralds; }

        /** Organic mass: the brain's unit-spawn budget (1.1.2 organic system). Fully independent of emeralds/level. */
        public int getOrganicPoints() { return organicPoints; }
        public void addOrganicPoints(int n) { organicPoints = Math.max(ORGANIC_FLOOR, organicPoints + n); setDirty(); }
        /** Spend {@code n} organic points on a unit; the balance may dip to {@link #ORGANIC_FLOOR} (deploy on a
         *  little credit, like the emerald economy). Returns the amount actually spent. */
        public int spendOrganicPoints(int n) {
            if (n <= 0) return 0;
            int before = organicPoints;
            organicPoints = Math.max(ORGANIC_FLOOR, organicPoints - n);
            setDirty();
            return before - organicPoints;
        }
        /** Force the village stage (1..{@value #MAX_LEVEL}); clears the pending emeralds so it doesn't re-promote. */
        public void setLevel(int lvl) { level = Math.max(1, Math.min(MAX_LEVEL, lvl)); emeralds = 0; setDirty(); }
        public boolean isMaxLevel() { return level >= MAX_LEVEL; }
        public int costForNext() { return isMaxLevel() ? -1 : scaledCost(level); }
        public long getLastIncomeDay() { return lastIncomeDay; }
        public void setLastIncomeDay(long day) { lastIncomeDay = day; setDirty(); }

        /**
         * Change the village's emerald balance. Positive {@code n}: accumulate and level UP while affordable
         * (unchanged). Negative {@code n}: spend, and if the balance goes below 0 at level &gt; 1 the village
         * DEVOLVES one stage at a time -- dropping a level and refunding the cost that bought it (so a small
         * overspend cashes the stage in rather than going deep negative); e.g. level 2 @ 0, spend 10 -&gt; level 1
         * @ 22 (=-10+32). At level 1 the balance simply floors at {@link #MIN_EMERALDS_LEVEL1}. On a devolve the
         * lower phase banner (e.g. SETTLEMENT PHASE) is re-broadcast. Returns +levels gained, 0, or -levels lost.
         */
        public int addEmeralds(int n) {
            if (n == 0) return 0;
            emeralds += n;
            int delta = 0;
            if (n > 0) {
                while (!isMaxLevel()) {
                    int cost = scaledCost(level);
                    if (emeralds < cost) break;
                    emeralds -= cost;
                    level++;
                    delta++;
                }
            } else {
                while (emeralds < 0 && level > 1) {
                    level--;
                    emeralds += scaledCost(level); // refund the cost that bought the stage we just left
                    delta--;
                }
                if (level <= 1 && emeralds < MIN_EMERALDS_LEVEL1) emeralds = MIN_EMERALDS_LEVEL1; // floor at lvl 1
            }
            setDirty();
            if (delta < 0) { // a devolve happened -> re-show the (now lower) phase, e.g. "SETTLEMENT PHASE"
                MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) BrainNodeEvents.broadcastPhaseBanner(server, level);
            }
            return delta;
        }

        /** Spend {@code n} gems on a reflex. ALWAYS succeeds (n &gt; 0): the balance may go negative / devolve /
         *  floor via {@link #addEmeralds}, so the village can defend itself on credit (and devolve under pressure). */
        public boolean spendEmeralds(int n) {
            if (n <= 0) return false;
            addEmeralds(-n);
            return true;
        }
    }

    /** The economy for the village this brainNode belongs to (brainNodes within {@value #CLUSTER} share one). */
    public Econ econ(GlobalPos brainNode) {
        for (Map.Entry<GlobalPos, Econ> e : econs.entrySet()) {
            GlobalPos key = e.getKey();
            if (key.dimension().equals(brainNode.dimension())
                    && key.pos().distSqr(brainNode.pos()) <= CLUSTER * CLUSTER) {
                return e.getValue();
            }
        }
        Econ econ = new Econ();
        econs.put(brainNode, econ); // new village, keyed by this brain node's position
        setDirty();
        return econ;
    }

    /**
     * Wipe ALL village state at/around {@code brainNode} so a freshly-placed block there starts at stage 1
     * (used when the unleashed boss reverts to a brainNode on a win). Removes the proximity-matched economy
     * and every per-brainNode once-only flag within {@value #CLUSTER} blocks, so wall/defenders/dome/expansion
     * all rebuild from scratch.
     */
    public void eraseVillage(GlobalPos brainNode) {
        boolean changed = econs.keySet().removeIf(k -> nearCluster(k, brainNode));
        changed |= servicedCenters.removeIf(k -> nearCluster(k, brainNode));
        changed |= announced.removeIf(k -> nearCluster(k, brainNode));
        changed |= defendersBuilt.removeIf(k -> nearCluster(k, brainNode));
        changed |= domeBuilt.removeIf(k -> nearCluster(k, brainNode));
        changed |= expanded.removeIf(k -> nearCluster(k, brainNode));
        changed |= wallPlans.keySet().removeIf(k -> nearCluster(k, brainNode));
        changed |= outerWallPlans.keySet().removeIf(k -> nearCluster(k, brainNode));
        changed |= outerDefendersBuilt.removeIf(k -> nearCluster(k, brainNode));
        if (changed) setDirty();
    }

    private static boolean nearCluster(GlobalPos k, GlobalPos g) {
        return k.dimension().equals(g.dimension()) && k.pos().distSqr(g.pos()) <= CLUSTER * CLUSTER;
    }

    // --- placed blocks / serviced village centers ---

    public Set<GlobalPos> blocks() { return blocks; }

    public void addBlock(GlobalPos pos) { if (blocks.add(pos)) setDirty(); }
    public void removeBlock(GlobalPos pos) { if (blocks.remove(pos)) setDirty(); }

    public Set<GlobalPos> nodes() { return nodes; }
    public void addNode(GlobalPos pos) { if (nodes.add(pos)) setDirty(); }
    public void removeNode(GlobalPos pos) { if (nodes.remove(pos)) setDirty(); }

    public boolean isServiced(GlobalPos center) { return servicedCenters.contains(center); }
    public void addServiced(GlobalPos center) { if (servicedCenters.add(center)) setDirty(); }

    // --- brain-node discovery banner (fires once per node, the first time any player comes near it) ---

    public boolean isAnnounced(GlobalPos brainNode) { return announced.contains(brainNode); }
    public void markAnnounced(GlobalPos brainNode) { if (announced.add(brainNode)) setDirty(); }

    // --- defensive wall plans (one per brainNode) ---

    public Collection<WallPlan> wallPlans() { return wallPlans.values(); }
    public WallPlan getWallPlan(GlobalPos brainNode) { return wallPlans.get(brainNode); }
    public void putWallPlan(WallPlan plan) { wallPlans.put(plan.brainNode(), plan); setDirty(); }
    public void removeWallPlan(GlobalPos brainNode) {
        if (wallPlans.remove(brainNode) != null) setDirty();
        if (defendersBuilt.remove(brainNode)) setDirty(); // new wall -> let the gate turrets be rebuilt
    }

    // --- stage-3 OUTER obsidian wall (one per brainNode) ---

    public Collection<WallPlan> outerWallPlans() { return outerWallPlans.values(); }
    public WallPlan getOuterWallPlan(GlobalPos brainNode) { return outerWallPlans.get(brainNode); }
    public void putOuterWallPlan(WallPlan plan) { outerWallPlans.put(plan.brainNode(), plan); setDirty(); }
    public boolean areOuterDefendersBuilt(GlobalPos brainNode) { return outerDefendersBuilt.contains(brainNode); }
    public void markOuterDefendersBuilt(GlobalPos brainNode) { if (outerDefendersBuilt.add(brainNode)) setDirty(); }

    // --- level-2 gate turrets (built once per brainNode) ---

    public boolean areDefendersBuilt(GlobalPos brainNode) { return defendersBuilt.contains(brainNode); }
    public void markDefendersBuilt(GlobalPos brainNode) { if (defendersBuilt.add(brainNode)) setDirty(); }

    // --- level-4 reinforced-glass dome (built once per brainNode) ---

    public boolean isDomeBuilt(GlobalPos brainNode) { return domeBuilt.contains(brainNode); }
    public void markDomeBuilt(GlobalPos brainNode) { if (domeBuilt.add(brainNode)) setDirty(); }

    // --- level-3 expansion: settlers found new buildings outside the wall (run once per brainNode) ---

    public boolean isExpanded(GlobalPos brainNode) { return expanded.contains(brainNode); }
    public void markExpanded(GlobalPos brainNode) { if (expanded.add(brainNode)) setDirty(); }

    // --- who placed a village bell (so the brain node that rises there rewards them) ---

    public void recordBellPlacer(GlobalPos bell, java.util.UUID player) { bellPlacers.put(bell, player); setDirty(); }
    /** The player who placed the bell at {@code bell}, removing the record (one-time reward); null if none. */
    public java.util.UUID takeBellPlacer(GlobalPos bell) {
        java.util.UUID u = bellPlacers.remove(bell);
        if (u != null) setDirty();
        return u;
    }

    // --- persistence ---

    public static BrainNodeData load(CompoundTag tag) {
        BrainNodeData d = new BrainNodeData();
        readPositions(tag, "blocks", d.blocks);
        readPositions(tag, "nodes", d.nodes);
        readPositions(tag, "serviced", d.servicedCenters);
        readPositions(tag, "announced", d.announced);
        readPositions(tag, "defenders", d.defendersBuilt);
        readPositions(tag, "outerDefenders", d.outerDefendersBuilt);
        readPositions(tag, "dome", d.domeBuilt);
        readPositions(tag, "expanded", d.expanded);
        ListTag econList = tag.getList("econs", Tag.TAG_COMPOUND);
        for (int i = 0; i < econList.size(); i++) {
            CompoundTag c = econList.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            GlobalPos key = GlobalPos.of(dim, BlockPos.of(c.getLong("pos")));
            Econ e = d.new Econ();
            e.level = Math.max(1, c.getInt("level"));
            e.emeralds = c.getInt("emeralds");
            e.organicPoints = c.getInt("organic");
            e.lastIncomeDay = c.contains("income") ? c.getLong("income") : Long.MIN_VALUE;
            d.econs.put(key, e);
        }
        // Migrate a pre-per-village global economy onto one of the existing brainNodes.
        if (econList.isEmpty() && tag.contains("level") && !d.blocks.isEmpty()) {
            Econ e = d.new Econ();
            e.level = Math.max(1, tag.getInt("level"));
            e.emeralds = tag.getInt("emeralds");
            e.lastIncomeDay = tag.contains("lastIncomeDay") ? tag.getLong("lastIncomeDay") : Long.MIN_VALUE;
            d.econs.put(d.blocks.iterator().next(), e);
        }
        ListTag walls = tag.getList("wallPlans", Tag.TAG_COMPOUND);
        for (int i = 0; i < walls.size(); i++) {
            WallPlan plan = WallPlan.load(walls.getCompound(i));
            d.wallPlans.put(plan.brainNode(), plan);
        }
        ListTag outerWalls = tag.getList("outerWallPlans", Tag.TAG_COMPOUND);
        for (int i = 0; i < outerWalls.size(); i++) {
            WallPlan plan = WallPlan.load(outerWalls.getCompound(i));
            d.outerWallPlans.put(plan.brainNode(), plan);
        }
        ListTag bells = tag.getList("bellPlacers", Tag.TAG_COMPOUND);
        for (int i = 0; i < bells.size(); i++) {
            CompoundTag c = bells.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            d.bellPlacers.put(GlobalPos.of(dim, BlockPos.of(c.getLong("pos"))), c.getUUID("player"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        writePositions(tag, "blocks", blocks);
        writePositions(tag, "nodes", nodes);
        writePositions(tag, "serviced", servicedCenters);
        writePositions(tag, "announced", announced);
        writePositions(tag, "defenders", defendersBuilt);
        writePositions(tag, "outerDefenders", outerDefendersBuilt);
        writePositions(tag, "dome", domeBuilt);
        writePositions(tag, "expanded", expanded);
        ListTag econList = new ListTag();
        for (Map.Entry<GlobalPos, Econ> e : econs.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.getKey().dimension().location().toString());
            c.putLong("pos", e.getKey().pos().asLong());
            c.putInt("level", e.getValue().level);
            c.putInt("emeralds", e.getValue().emeralds);
            c.putInt("organic", e.getValue().organicPoints);
            c.putLong("income", e.getValue().lastIncomeDay);
            econList.add(c);
        }
        tag.put("econs", econList);
        ListTag walls = new ListTag();
        for (WallPlan plan : wallPlans.values()) walls.add(plan.save());
        tag.put("wallPlans", walls);
        ListTag outerWalls = new ListTag();
        for (WallPlan plan : outerWallPlans.values()) outerWalls.add(plan.save());
        tag.put("outerWallPlans", outerWalls);
        ListTag bells = new ListTag();
        for (Map.Entry<GlobalPos, java.util.UUID> e : bellPlacers.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.getKey().dimension().location().toString());
            c.putLong("pos", e.getKey().pos().asLong());
            c.putUUID("player", e.getValue());
            bells.add(c);
        }
        tag.put("bellPlacers", bells);
        return tag;
    }

    private static void writePositions(CompoundTag tag, String key, Set<GlobalPos> set) {
        ListTag list = new ListTag();
        for (GlobalPos gp : set) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", gp.dimension().location().toString());
            c.putLong("pos", gp.pos().asLong());
            list.add(c);
        }
        tag.put(key, list);
    }

    private static void readPositions(CompoundTag tag, String key, Set<GlobalPos> out) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            out.add(GlobalPos.of(dim, BlockPos.of(c.getLong("pos"))));
        }
    }
}
