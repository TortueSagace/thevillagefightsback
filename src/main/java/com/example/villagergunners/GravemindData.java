package com.example.villagergunners;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.villagergunners.wall.WallPlan;

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
 * The single, world-global Village Gravemind state, shared across ALL villages: the current level,
 * emeralds accumulated toward the next level, the day the sunset income last ran, and the set of
 * placed gravemind block positions (so income/visuals can find them). Stored on the overworld's data
 * storage, so it persists with the save and is the same no matter which gravemind you interact with.
 */
public class GravemindData extends SavedData {
    private static final String NAME = "gobacktoyourworld_gravemind";

    public static final int MAX_LEVEL = 4;
    // costForNext(level) = emeralds needed to advance FROM that level. (Tunable.)
    private static final int[] COST_TO_ADVANCE = {0, 16, 128, 256}; // 1->2=16, 2->3=128, 3->4=256

    // Per-village economy: graveminds within CLUSTER blocks share one Econ; graveminds farther apart are
    // completely independent villages, each with its own level/emeralds/income.
    private static final double CLUSTER = 100.0;

    private final Map<GlobalPos, Econ> econs = new HashMap<>();   // one economy per village cluster
    private final Set<GlobalPos> blocks = new HashSet<>();        // placed gravemind blocks
    private final Set<GlobalPos> servicedCenters = new HashSet<>(); // village centers already given a gravemind
    private final Map<GlobalPos, WallPlan> wallPlans = new HashMap<>(); // computed defensive wall per gravemind
    private final Set<GlobalPos> defendersBuilt = new HashSet<>();  // graveminds whose level-2 gate turrets are placed
    private final Set<GlobalPos> domeBuilt = new HashSet<>();       // graveminds whose level-4 glass dome is placed
    private final Set<GlobalPos> expanded = new HashSet<>();        // graveminds that have run their stage-3 expansion
    private final Map<GlobalPos, java.util.UUID> bellPlacers = new HashMap<>(); // bell pos -> player who placed it

    public GravemindData() {}

    public static GravemindData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(GravemindData::load, GravemindData::new, NAME);
    }

    // --- per-village level / emerald economy ---

    /** The level/emerald economy of one village. */
    public final class Econ {
        private int level = 1;
        private int emeralds = 0;
        private long lastIncomeDay = Long.MIN_VALUE;

        public int getLevel() { return level; }
        public int getEmeralds() { return emeralds; }
        public boolean isMaxLevel() { return level >= MAX_LEVEL; }
        public int costForNext() { return isMaxLevel() ? -1 : COST_TO_ADVANCE[level]; }
        public long getLastIncomeDay() { return lastIncomeDay; }
        public void setLastIncomeDay(long day) { lastIncomeDay = day; setDirty(); }

        /** Add emeralds toward evolution; returns how many levels were gained. */
        public int addEmeralds(int n) {
            if (n <= 0 || isMaxLevel()) return 0;
            emeralds += n;
            int gained = 0;
            while (!isMaxLevel() && emeralds >= COST_TO_ADVANCE[level]) {
                emeralds -= COST_TO_ADVANCE[level];
                level++;
                gained++;
            }
            if (isMaxLevel()) emeralds = 0;
            setDirty();
            return gained;
        }
    }

    /** The economy for the village this gravemind belongs to (graveminds within {@value #CLUSTER} share one). */
    public Econ econ(GlobalPos gravemind) {
        for (Map.Entry<GlobalPos, Econ> e : econs.entrySet()) {
            GlobalPos key = e.getKey();
            if (key.dimension().equals(gravemind.dimension())
                    && key.pos().distSqr(gravemind.pos()) <= CLUSTER * CLUSTER) {
                return e.getValue();
            }
        }
        Econ econ = new Econ();
        econs.put(gravemind, econ); // new village, keyed by this gravemind's position
        setDirty();
        return econ;
    }

    // --- placed blocks / serviced village centers ---

    public Set<GlobalPos> blocks() { return blocks; }

    public void addBlock(GlobalPos pos) { if (blocks.add(pos)) setDirty(); }
    public void removeBlock(GlobalPos pos) { if (blocks.remove(pos)) setDirty(); }

    public boolean isServiced(GlobalPos center) { return servicedCenters.contains(center); }
    public void addServiced(GlobalPos center) { if (servicedCenters.add(center)) setDirty(); }

    // --- defensive wall plans (one per gravemind) ---

    public Collection<WallPlan> wallPlans() { return wallPlans.values(); }
    public WallPlan getWallPlan(GlobalPos gravemind) { return wallPlans.get(gravemind); }
    public void putWallPlan(WallPlan plan) { wallPlans.put(plan.gravemind(), plan); setDirty(); }
    public void removeWallPlan(GlobalPos gravemind) {
        if (wallPlans.remove(gravemind) != null) setDirty();
        if (defendersBuilt.remove(gravemind)) setDirty(); // new wall -> let the gate turrets be rebuilt
    }

    // --- level-2 gate turrets (built once per gravemind) ---

    public boolean areDefendersBuilt(GlobalPos gravemind) { return defendersBuilt.contains(gravemind); }
    public void markDefendersBuilt(GlobalPos gravemind) { if (defendersBuilt.add(gravemind)) setDirty(); }

    // --- level-4 reinforced-glass dome (built once per gravemind) ---

    public boolean isDomeBuilt(GlobalPos gravemind) { return domeBuilt.contains(gravemind); }
    public void markDomeBuilt(GlobalPos gravemind) { if (domeBuilt.add(gravemind)) setDirty(); }

    // --- level-3 expansion: settlers found new buildings outside the wall (run once per gravemind) ---

    public boolean isExpanded(GlobalPos gravemind) { return expanded.contains(gravemind); }
    public void markExpanded(GlobalPos gravemind) { if (expanded.add(gravemind)) setDirty(); }

    // --- who placed a village bell (so the gravemind that rises there rewards them) ---

    public void recordBellPlacer(GlobalPos bell, java.util.UUID player) { bellPlacers.put(bell, player); setDirty(); }
    /** The player who placed the bell at {@code bell}, removing the record (one-time reward); null if none. */
    public java.util.UUID takeBellPlacer(GlobalPos bell) {
        java.util.UUID u = bellPlacers.remove(bell);
        if (u != null) setDirty();
        return u;
    }

    // --- persistence ---

    public static GravemindData load(CompoundTag tag) {
        GravemindData d = new GravemindData();
        readPositions(tag, "blocks", d.blocks);
        readPositions(tag, "serviced", d.servicedCenters);
        readPositions(tag, "defenders", d.defendersBuilt);
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
            e.lastIncomeDay = c.contains("income") ? c.getLong("income") : Long.MIN_VALUE;
            d.econs.put(key, e);
        }
        // Migrate a pre-per-village global economy onto one of the existing graveminds.
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
            d.wallPlans.put(plan.gravemind(), plan);
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
        writePositions(tag, "serviced", servicedCenters);
        writePositions(tag, "defenders", defendersBuilt);
        writePositions(tag, "dome", domeBuilt);
        writePositions(tag, "expanded", expanded);
        ListTag econList = new ListTag();
        for (Map.Entry<GlobalPos, Econ> e : econs.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.getKey().dimension().location().toString());
            c.putLong("pos", e.getKey().pos().asLong());
            c.putInt("level", e.getValue().level);
            c.putInt("emeralds", e.getValue().emeralds);
            c.putLong("income", e.getValue().lastIncomeDay);
            econList.add(c);
        }
        tag.put("econs", econList);
        ListTag walls = new ListTag();
        for (WallPlan plan : wallPlans.values()) walls.add(plan.save());
        tag.put("wallPlans", walls);
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
