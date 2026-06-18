package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Global, persisted registry of every creature held in Limbo and the gravel "leash(es)" that anchor it.
 *
 * <p>Each {@link Entry} is ONE leash: a stable {@code token}, the live Limbo {@code creature} it tethers, the
 * origin dimension it returns to, the gravel's current position (when {@code held} is false), the captured
 * name + max-health + entity type id (the last two drive the &gt;150 HP instability and the gravel's colour),
 * and bookkeeping (entry time, name-tag marker, self-destruct deadline).
 *
 * <p>Normally one creature = one leash. A banished WITHER STORM is the exception: it is ONE creature anchored
 * by MANY leashes (its "parts"), all sharing the same {@code creature} UUID with {@code stormPart=true}. So
 * the registry is indexed three ways: by token (primary), by gravel position (placed leashes only), and by
 * creature (one-to-many, for finding a storm's siblings). Stored on the overworld's data storage.
 */
public class LimboData extends SavedData {
    private static final String NAME = "thevillagefightsback_limbo";

    /** One gravel leash tethering a live Limbo creature back to a spot in {@link #originDim}. */
    public static final class Entry {
        public final UUID token;          // unique per leash (survives the block <-> item round-trip)
        public final UUID creature;       // the live Limbo entity (shared across a storm's part-leashes)
        public ResourceKey<Level> originDim; // the dimension the gravel currently sits in (= the return dim)
        public BlockPos gravelPos;        // current resting cell (valid when !held)
        public final long entryTime;
        public final String name;
        public UUID marker;               // floating name-tag armor stand, or null
        public final String typeId;       // banished entity's type id (for the client gravel colour), or ""
        public final float maxHealth;     // BASE max-health captured at banish (> 150 => unstable in Limbo)
        public final boolean stormPart;   // part of a multi-gravel wither storm
        public boolean held;              // currently carried as an item (no physical block; countdown paused)
        public long unstableUntil;        // game-time the placed gravel self-destructs (0 = stable)

        public Entry(UUID token, UUID creature, ResourceKey<Level> originDim, BlockPos gravelPos, long entryTime,
                     String name, UUID marker, String typeId, float maxHealth, boolean stormPart,
                     boolean held, long unstableUntil) {
            this.token = token;
            this.creature = creature;
            this.originDim = originDim;
            this.gravelPos = gravelPos;
            this.entryTime = entryTime;
            this.name = name;
            this.marker = marker;
            this.typeId = typeId == null ? "" : typeId;
            this.maxHealth = maxHealth;
            this.stormPart = stormPart;
            this.held = held;
            this.unstableUntil = unstableUntil;
        }

        public boolean unstable() { return maxHealth > UNSTABLE_HP; }
    }

    /** Creatures stronger than this are not stable in Limbo: their placed gravel self-destructs after a delay. */
    public static final float UNSTABLE_HP = 150.0F;

    private final Map<UUID, Entry> byToken = new HashMap<>();
    private final Map<String, Entry> byGravel = new HashMap<>();          // key = dim '@' packedPos (placed only)
    private final Map<UUID, Set<Entry>> byCreature = new HashMap<>();     // one creature -> its leash(es)

    public LimboData() {}

    public static LimboData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LimboData::load, LimboData::new, NAME);
    }

    private static String gravelKey(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "@" + pos.asLong();
    }

    /** Number of distinct CREATURES held (a storm with many parts counts once -> the cap is per-creature). */
    public int creatureCount() { return byCreature.size(); }

    public Entry getByToken(UUID token) { return byToken.get(token); }

    public Entry getByGravel(ResourceKey<Level> dim, BlockPos pos) { return byGravel.get(gravelKey(dim, pos)); }

    public boolean hasGravelAt(ResourceKey<Level> dim, BlockPos pos) { return byGravel.containsKey(gravelKey(dim, pos)); }

    /** Every leash tethering {@code creature} (empty if none). For a storm this is all of its part-leashes. */
    public Set<Entry> getByCreature(UUID creature) {
        Set<Entry> s = byCreature.get(creature);
        return s == null ? Set.of() : new HashSet<>(s);
    }

    public boolean isCreatureRegistered(UUID creature) {
        Set<Entry> s = byCreature.get(creature);
        return s != null && !s.isEmpty();
    }

    /** A snapshot list of every leash (safe to iterate while removing). */
    public List<Entry> entries() { return new ArrayList<>(byToken.values()); }

    public void add(Entry e) {
        byToken.put(e.token, e);
        byCreature.computeIfAbsent(e.creature, k -> new HashSet<>()).add(e);
        if (!e.held) byGravel.put(gravelKey(e.originDim, e.gravelPos), e);
        setDirty();
    }

    /** Mark a placed leash as now carried (its block became an item): drop its gravel-pos index, pause countdown. */
    public void markHeld(Entry e) {
        if (!e.held) byGravel.remove(gravelKey(e.originDim, e.gravelPos));
        e.held = true;
        e.unstableUntil = 0;
        setDirty();
    }

    /** Mark a carried leash as placed again at {@code dim}/{@code pos} (item became a block): re-index (possibly
     *  in a new dimension -- the gravel returns wherever it now sits), restart the self-destruct countdown. */
    public void markPlaced(Entry e, ResourceKey<Level> dim, BlockPos pos, UUID marker, long unstableUntil) {
        if (!e.held) byGravel.remove(gravelKey(e.originDim, e.gravelPos));
        e.held = false;
        e.originDim = dim;
        e.gravelPos = pos;
        e.marker = marker;
        e.unstableUntil = unstableUntil;
        byGravel.put(gravelKey(dim, pos), e);
        setDirty();
    }

    /** A placed leash's gravel fell to a new resting cell: re-index it (creature stays). */
    public void moveGravel(Entry e, BlockPos to) {
        if (!e.held) byGravel.remove(gravelKey(e.originDim, e.gravelPos));
        e.gravelPos = to;
        byGravel.put(gravelKey(e.originDim, to), e);
        setDirty();
    }

    public Entry removeByToken(UUID token) {
        Entry e = byToken.remove(token);
        if (e != null) detach(e);
        return e;
    }

    public Entry removeByGravel(ResourceKey<Level> dim, BlockPos pos) {
        Entry e = byGravel.get(gravelKey(dim, pos));
        if (e != null) { byToken.remove(e.token); detach(e); }
        return e;
    }

    /** Remove EVERY leash tethering {@code creature} (all of a storm's parts); returns the removed set. */
    public Set<Entry> removeByCreature(UUID creature) {
        Set<Entry> s = byCreature.remove(creature);
        if (s == null) return Set.of();
        for (Entry e : s) {
            byToken.remove(e.token);
            if (!e.held) byGravel.remove(gravelKey(e.originDim, e.gravelPos));
        }
        setDirty();
        return s;
    }

    private void detach(Entry e) {
        if (!e.held) byGravel.remove(gravelKey(e.originDim, e.gravelPos));
        Set<Entry> s = byCreature.get(e.creature);
        if (s != null) { s.remove(e); if (s.isEmpty()) byCreature.remove(e.creature); }
        setDirty();
    }

    // --- persistence ---
    public static LimboData load(CompoundTag tag) {
        LimboData d = new LimboData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            UUID token = c.hasUUID("token") ? c.getUUID("token") : UUID.randomUUID();
            UUID creature = c.getUUID("uuid");
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            BlockPos pos = BlockPos.of(c.getLong("pos"));
            UUID marker = c.hasUUID("marker") ? c.getUUID("marker") : null;
            Entry e = new Entry(token, creature, dim, pos, c.getLong("time"), c.getString("name"), marker,
                    c.getString("type"), c.getFloat("maxhp"), c.getBoolean("storm"),
                    c.getBoolean("held"), c.getLong("unstable"));
            d.byToken.put(token, e);
            d.byCreature.computeIfAbsent(creature, k -> new HashSet<>()).add(e);
            if (!e.held) d.byGravel.put(gravelKey(dim, pos), e);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : byToken.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("token", e.token);
            c.putUUID("uuid", e.creature);
            c.putString("dim", e.originDim.location().toString());
            c.putLong("pos", e.gravelPos.asLong());
            c.putLong("time", e.entryTime);
            c.putString("name", e.name == null ? "" : e.name);
            if (e.marker != null) c.putUUID("marker", e.marker);
            c.putString("type", e.typeId);
            c.putFloat("maxhp", e.maxHealth);
            c.putBoolean("storm", e.stormPart);
            c.putBoolean("held", e.held);
            c.putLong("unstable", e.unstableUntil);
            list.add(c);
        }
        tag.put("entries", list);
        return tag;
    }
}
