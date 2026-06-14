package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Global, persisted registry of every creature currently held in Limbo. For each one we remember the
 * gravel-marker's CURRENT position (it moves as the gravel falls), the dimension it returns to, when it
 * arrived (for the "release the most ancient" rule), the creature's name (shown on the marker's floating
 * name tag), and that name-tag marker entity's UUID. Indexed by creature UUID and by gravel position.
 * Stored on the overworld's data storage.
 */
public class LimboData extends SavedData {
    private static final String NAME = "gobacktoyourworld_limbo";

    /** One held creature: its return marker (current gravel pos), arrival time, name and name-tag entity. */
    public static final class Entry {
        public final UUID creature;
        public final ResourceKey<Level> originDim;
        public final BlockPos gravelPos;
        public final long entryTime;
        public final String name;
        public final UUID marker; // floating name-tag armor stand, or null

        public Entry(UUID creature, ResourceKey<Level> originDim, BlockPos gravelPos, long entryTime,
                     String name, UUID marker) {
            this.creature = creature;
            this.originDim = originDim;
            this.gravelPos = gravelPos;
            this.entryTime = entryTime;
            this.name = name;
            this.marker = marker;
        }
    }

    private final Map<UUID, Entry> byCreature = new HashMap<>();
    private final Map<String, Entry> byGravel = new HashMap<>(); // key = dim '@' packedPos

    public LimboData() {}

    public static LimboData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LimboData::load, LimboData::new, NAME);
    }

    private static String gravelKey(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "@" + pos.asLong();
    }

    public int size() { return byCreature.size(); }

    public Entry getByCreature(UUID id) { return byCreature.get(id); }

    public Entry getByGravel(ResourceKey<Level> dim, BlockPos pos) { return byGravel.get(gravelKey(dim, pos)); }

    public boolean hasGravelAt(ResourceKey<Level> dim, BlockPos pos) { return byGravel.containsKey(gravelKey(dim, pos)); }

    /** A snapshot list of all entries (safe to iterate while removing). */
    public List<Entry> entries() { return new ArrayList<>(byCreature.values()); }

    public void add(Entry e) {
        byCreature.put(e.creature, e);
        byGravel.put(gravelKey(e.originDim, e.gravelPos), e);
        setDirty();
    }

    public Entry removeByCreature(UUID id) {
        Entry e = byCreature.remove(id);
        if (e != null) { byGravel.remove(gravelKey(e.originDim, e.gravelPos)); setDirty(); }
        return e;
    }

    public Entry removeByGravel(ResourceKey<Level> dim, BlockPos pos) {
        Entry e = byGravel.remove(gravelKey(dim, pos));
        if (e != null) { byCreature.remove(e.creature); setDirty(); }
        return e;
    }

    // --- persistence ---
    public static LimboData load(CompoundTag tag) {
        LimboData d = new LimboData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            UUID id = c.getUUID("uuid");
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            BlockPos pos = BlockPos.of(c.getLong("pos"));
            String name = c.getString("name");
            UUID marker = c.hasUUID("marker") ? c.getUUID("marker") : null;
            Entry e = new Entry(id, dim, pos, c.getLong("time"), name, marker);
            d.byCreature.put(id, e);
            d.byGravel.put(gravelKey(dim, pos), e);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : byCreature.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("uuid", e.creature);
            c.putString("dim", e.originDim.location().toString());
            c.putLong("pos", e.gravelPos.asLong());
            c.putLong("time", e.entryTime);
            c.putString("name", e.name == null ? "" : e.name);
            if (e.marker != null) c.putUUID("marker", e.marker);
            list.add(c);
        }
        tag.put("entries", list);
        return tag;
    }
}
