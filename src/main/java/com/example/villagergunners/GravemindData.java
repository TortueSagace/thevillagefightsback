package com.example.villagergunners;

import java.util.HashSet;
import java.util.Set;

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

    public static final int MAX_LEVEL = 3;
    // costForNext(level) = emeralds needed to advance FROM that level. (Tunable.)
    private static final int[] COST_TO_ADVANCE = {0, 64, 192}; // index by current level: 1->2 =64, 2->3 =192

    private int level = 1;
    private int emeralds = 0;
    private long lastIncomeDay = Long.MIN_VALUE;
    private final Set<GlobalPos> blocks = new HashSet<>();        // placed gravemind blocks
    private final Set<GlobalPos> servicedCenters = new HashSet<>(); // village centers already given a gravemind

    public GravemindData() {}

    public static GravemindData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(GravemindData::load, GravemindData::new, NAME);
    }

    // --- level / emerald economy ---

    public int getLevel() { return level; }
    public int getEmeralds() { return emeralds; }
    public boolean isMaxLevel() { return level >= MAX_LEVEL; }

    /** Emeralds needed to reach the next level, or -1 at max level. */
    public int costForNext() {
        return isMaxLevel() ? -1 : COST_TO_ADVANCE[level];
    }

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

    // --- sunset income bookkeeping ---

    public long getLastIncomeDay() { return lastIncomeDay; }
    public void setLastIncomeDay(long day) { lastIncomeDay = day; setDirty(); }

    // --- placed blocks / serviced village centers ---

    public Set<GlobalPos> blocks() { return blocks; }

    public void addBlock(GlobalPos pos) { if (blocks.add(pos)) setDirty(); }
    public void removeBlock(GlobalPos pos) { if (blocks.remove(pos)) setDirty(); }

    public boolean isServiced(GlobalPos center) { return servicedCenters.contains(center); }
    public void addServiced(GlobalPos center) { if (servicedCenters.add(center)) setDirty(); }

    // --- persistence ---

    public static GravemindData load(CompoundTag tag) {
        GravemindData d = new GravemindData();
        d.level = Math.max(1, tag.getInt("level"));
        d.emeralds = tag.getInt("emeralds");
        d.lastIncomeDay = tag.contains("lastIncomeDay") ? tag.getLong("lastIncomeDay") : Long.MIN_VALUE;
        readPositions(tag, "blocks", d.blocks);
        readPositions(tag, "serviced", d.servicedCenters);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("level", level);
        tag.putInt("emeralds", emeralds);
        tag.putLong("lastIncomeDay", lastIncomeDay);
        writePositions(tag, "blocks", blocks);
        writePositions(tag, "serviced", servicedCenters);
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
