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
    /** Organic mass is clamped to this floor everywhere; 0 forbids any negative organic-point balance. */
    public static final int ORGANIC_FLOOR = 0;
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
    /**
     * Dead brain nodes: husks a village left behind, and the ones Survival finds in villages.
     *
     * <p>Kept apart from {@link #blocks()} on purpose -- a dead node must stay invisible to everything that
     * asks "is there a brain in reach", which is what that set is for. This one exists for the single rule
     * that a dead node SHOULD answer: a village with a husk in it already has a brain, dormant or not, so a
     * bell there must not raise a second one.
     */
    private final Set<GlobalPos> deadNodes = new HashSet<>();
    private final Set<GlobalPos> announced = new HashSet<>();       // brain nodes whose discovery banner already fired (to any player)
    private final Map<GlobalPos, WallPlan> wallPlans = new HashMap<>(); // computed defensive wall per brainNode
    private final Map<GlobalPos, WallPlan> outerWallPlans = new HashMap<>(); // stage-3 OUTER obsidian wall per brainNode
    private final Set<GlobalPos> defendersBuilt = new HashSet<>();  // brainNodes whose level-2 gate turrets are placed
    private final Set<GlobalPos> outerDefendersBuilt = new HashSet<>(); // brainNodes whose OUTER-wall turrets are placed
    private final Set<GlobalPos> domeBuilt = new HashSet<>();       // brainNodes whose level-4 glass dome is placed
    private final Set<GlobalPos> expanded = new HashSet<>();        // brainNodes that have run their stage-3 expansion
    private final Set<GlobalPos> revived = new HashSet<>();         // brainNodes woken inside a village that was already there
    private final Map<GlobalPos, java.util.UUID> bellPlacers = new HashMap<>(); // bell pos -> player who placed it
    private final Map<GlobalPos, GlobalPos> nodeFurnaces = new HashMap<>(); // brainNode -> the ONE furnace it fuels

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
        // SURVIVAL only: the metal tribute a stage costs on top of its emeralds. Fed to the node like emeralds
        // are, held until the stage they pay for is reached, and then consumed with it.
        /**
         * SURVIVAL only: the item tribute held toward the next stage, keyed by item id.
         *
         * <p>Was three named ints (copper, gold, limbo) until 1.7.2, when what a stage costs became config
         * ({@link UpgradeTribute}). A map keyed by id rather than by {@code Item} deliberately: it is what
         * gets written to NBT, and a saved village whose config later names a different item then still
         * loads -- the old holding simply stops counting toward anything instead of crashing the world.
         */
        private final java.util.Map<String, Integer> tribute = new java.util.HashMap<>();

        public int getLevel() { return level; }
        public int getEmeralds() { return emeralds; }

        /** How much of {@code item} is banked toward the next stage. */
        public int held(net.minecraft.world.item.Item item) {
            net.minecraft.resources.ResourceLocation id =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            return id == null ? 0 : tribute.getOrDefault(id.toString(), 0);
        }

        /**
         * How much of {@code offered} the node will actually take: never more than the stage still wants.
         *
         * <p>Handing it a stack of 64 when 8 are needed should cost 8. Emeralds bank without limit because a
         * surplus rolls into the next stage, but the metal tribute is a fixed per-stage price -- taking the
         * whole stack would quietly eat 56 ingots for nothing.
         */
        public int wants(int offered, int have, int need) {
            return Math.max(0, Math.min(offered, need - have));
        }

        /**
         * Feed the node metal, taking only what the current stage still needs.
         *
         * @return how many stages it bought (metal alone can complete a stage whose emeralds are already in).
         */
        public int addTribute(java.util.Map<net.minecraft.world.item.Item, Integer> offered) {
            for (var e : offered.entrySet()) {
                net.minecraft.resources.ResourceLocation id =
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getKey());
                if (id != null) tribute.merge(id.toString(), e.getValue(), Integer::sum);
            }
            setDirty();
            return addEmeralds(0);   // re-run the promotion check: the emeralds may already be in
        }

        public static int needFor(int lvl, net.minecraft.world.item.Item item) {
            for (UpgradeTribute.Need n : UpgradeTribute.needsFor(lvl)) {
                if (n.item() == item) return n.count();
            }
            return 0;
        }

        /** Is the metal for the stage after {@code lvl} all in? */
        public boolean hasMetalFor(int lvl) {
            for (UpgradeTribute.Need n : UpgradeTribute.needsFor(lvl)) {
                if (held(n.item()) < n.count()) return false;
            }
            return true;
        }

        /**
         * True in a Survival world, where a stage costs metal as well as emeralds.
         *
         * <p>Read from the running server rather than passed down: every caller of {@code addEmeralds} would
         * otherwise have to know about it, and the mod already reaches for the server this way in the
         * infection and expansion managers.
         */
        private static boolean survival() {
            net.minecraft.server.MinecraftServer sv =
                    net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            return sv != null && WorldModeData.isSurvival(sv);
        }

        /** Organic mass: the brain's unit-spawn budget (1.1.2 organic system). Fully independent of emeralds/level. */
        public int getOrganicPoints() { return organicPoints; }
        public void addOrganicPoints(int n) { organicPoints = Math.max(ORGANIC_FLOOR, organicPoints + n); setDirty(); }
        /** Spend {@code n} organic points on a unit; the balance is clamped at {@link #ORGANIC_FLOOR} (0) so it
         *  can never go negative. Returns the amount actually spent. */
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
            emeralds += n;
            int delta = 0;
            if (n >= 0) {
                while (!isMaxLevel()) {
                    int cost = scaledCost(level);
                    if (emeralds < cost) break;
                    // SURVIVAL: emeralds alone no longer buy a stage. The metal has to be in too, and it is
                    // consumed with the emeralds so the next stage starts from nothing.
                    boolean paid = !survival();
                    if (!paid && hasMetalFor(level)) {
                        for (UpgradeTribute.Need need : UpgradeTribute.needsFor(level)) {
                            net.minecraft.resources.ResourceLocation id =
                                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(need.item());
                            if (id != null) tribute.merge(id.toString(), -need.count(), Integer::sum);
                        }
                        paid = true;
                    }
                    if (!paid) break;
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

    public Set<GlobalPos> deadNodes() { return deadNodes; }
    public void addDeadNode(GlobalPos pos) { if (deadNodes.add(pos)) setDirty(); }
    public void removeDeadNode(GlobalPos pos) { if (deadNodes.remove(pos)) setDirty(); }

    public boolean isServiced(GlobalPos center) { return servicedCenters.contains(center); }
    public void addServiced(GlobalPos center) { if (servicedCenters.add(center)) setDirty(); }

    // --- brain-node discovery banner (fires once per node, the first time any player comes near it) ---

    public boolean isAnnounced(GlobalPos brainNode) { return announced.contains(brainNode); }
    public void markAnnounced(GlobalPos brainNode) { if (announced.add(brainNode)) setDirty(); }

    // --- defensive wall plans (one per brainNode) ---

    /**
     * The Obliteration Furnace this brain node keeps lit, or null.
     *
     * <p>ONE per node, which is the whole point of storing it rather than searching for the nearest furnace: a
     * node that paid for a furnace goes on paying for that one, and a player who builds their own next door
     * never finds the village quietly funding it. A node whose furnace has been mined drops the link (see
     * {@code NodeFurnace}) and may raise another the next time General Darius musters.
     */
    public GlobalPos getNodeFurnace(GlobalPos brainNode) { return nodeFurnaces.get(brainNode); }
    public void setNodeFurnace(GlobalPos brainNode, GlobalPos furnace) {
        nodeFurnaces.put(brainNode, furnace);
        setDirty();
    }
    public void removeNodeFurnace(GlobalPos brainNode) {
        if (nodeFurnaces.remove(brainNode) != null) setDirty();
    }
    /** Every node that has one, as a copy safe to iterate while the upkeep pass drops dead links. */
    public java.util.List<Map.Entry<GlobalPos, GlobalPos>> nodeFurnaces() {
        return new java.util.ArrayList<>(nodeFurnaces.entrySet());
    }
    /** True if ANY node keeps a furnace, so the upkeep pass can cost nothing in the worlds that have none. */
    public boolean hasNodeFurnaces() { return !nodeFurnaces.isEmpty(); }

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

    // --- woken by a Village Spirit inside a village that was already standing ---

    /**
     * True for a node a spirit re-lit in place rather than one planted on open ground.
     *
     * <p>Deliberately NOT cleared by {@link #eraseVillage}, unlike every once-only flag above it. Those record
     * what this node has DONE, and erasing the village should make it do all of it again. This one records
     * what is on the ground around it, which no amount of erasing changes: the houses are still there, so
     * settlers would still build a second village through the first.
     */
    public boolean isRevived(GlobalPos brainNode) { return revived.contains(brainNode); }
    public void markRevived(GlobalPos brainNode) { if (revived.add(brainNode)) setDirty(); }

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
        readPositions(tag, "deadNodes", d.deadNodes);
        readPositions(tag, "announced", d.announced);
        readPositions(tag, "defenders", d.defendersBuilt);
        readPositions(tag, "outerDefenders", d.outerDefendersBuilt);
        readPositions(tag, "dome", d.domeBuilt);
        readPositions(tag, "expanded", d.expanded);
        readPositions(tag, "revived", d.revived);
        ListTag econList = tag.getList("econs", Tag.TAG_COMPOUND);
        for (int i = 0; i < econList.size(); i++) {
            CompoundTag c = econList.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            GlobalPos key = GlobalPos.of(dim, BlockPos.of(c.getLong("pos")));
            Econ e = d.new Econ();
            e.level = Math.max(1, c.getInt("level"));
            e.emeralds = c.getInt("emeralds");
            // 1.7.2 -- the three named metals became a map. Old saves are read back into it so a village
            // mid-way through paying for a stage does not lose what it had banked.
            if (c.contains("copper")) e.tribute.put("minecraft:copper_ingot", c.getInt("copper"));
            if (c.contains("gold")) e.tribute.put("minecraft:gold_ingot", c.getInt("gold"));
            if (c.contains("limbo")) e.tribute.put("thevillagefightsback:limbo_ingot", c.getInt("limbo"));
            CompoundTag trib = c.getCompound("tribute");
            for (String tk : trib.getAllKeys()) e.tribute.merge(tk, trib.getInt(tk), Integer::sum);
            e.organicPoints = Math.max(ORGANIC_FLOOR, c.getInt("organic"));
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
        ListTag furnaces = tag.getList("nodeFurnaces", Tag.TAG_COMPOUND);
        for (int i = 0; i < furnaces.size(); i++) {
            CompoundTag c = furnaces.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            ResourceKey<Level> fdim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("fdim")));
            d.nodeFurnaces.put(GlobalPos.of(dim, BlockPos.of(c.getLong("node"))),
                    GlobalPos.of(fdim, BlockPos.of(c.getLong("furnace"))));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        writePositions(tag, "blocks", blocks);
        writePositions(tag, "nodes", nodes);
        writePositions(tag, "serviced", servicedCenters);
        writePositions(tag, "deadNodes", deadNodes);
        writePositions(tag, "announced", announced);
        writePositions(tag, "defenders", defendersBuilt);
        writePositions(tag, "outerDefenders", outerDefendersBuilt);
        writePositions(tag, "dome", domeBuilt);
        writePositions(tag, "expanded", expanded);
        writePositions(tag, "revived", revived);
        ListTag econList = new ListTag();
        for (Map.Entry<GlobalPos, Econ> e : econs.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.getKey().dimension().location().toString());
            c.putLong("pos", e.getKey().pos().asLong());
            c.putInt("level", e.getValue().level);
            c.putInt("emeralds", e.getValue().emeralds);
            c.putInt("organic", e.getValue().organicPoints);
            CompoundTag trib = new CompoundTag();
            for (var t : e.getValue().tribute.entrySet()) {
                if (t.getValue() > 0) trib.putInt(t.getKey(), t.getValue());
            }
            c.put("tribute", trib);
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
        ListTag furnaces = new ListTag();
        for (Map.Entry<GlobalPos, GlobalPos> e : nodeFurnaces.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.getKey().dimension().location().toString());
            c.putLong("node", e.getKey().pos().asLong());
            c.putString("fdim", e.getValue().dimension().location().toString());
            c.putLong("furnace", e.getValue().pos().asLong());
            furnaces.add(c);
        }
        tag.put("nodeFurnaces", furnaces);
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
