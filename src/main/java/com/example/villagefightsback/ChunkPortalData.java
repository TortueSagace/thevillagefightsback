package com.example.villagefightsback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent ledger for the CHUNK PORTAL feature (v1.2.1), stored on the overworld's data storage (like
 * {@link BrainNodeData} / {@link LimboCubeData}). It tracks:
 * <ul>
 *   <li>{@code activated} -- whether the 1% transformation has been switched on. Set the first time a
 *       stage-3-or-higher brain node is in reach while {@link Config#chunkPortalsEnabled()} is true (see
 *       {@link ChunkPortalManager#manage}). Once on it stays on, so existing portals persist even if the
 *       village later devolves.</li>
 *   <li>{@code portals} -- overworld chunk keys ({@link net.minecraft.world.level.ChunkPos#toLong}) that have
 *       been turned into a 4-wall chunk portal (so a chunk is never built twice).</li>
 *   <li>{@code limboRings} -- Limbo chunk keys whose matching return ring + landing floor have been built
 *       (built the first time a player crosses into that footprint).</li>
 * </ul>
 * Per-portal emergence timers are transient (re-phased each session), so they are deliberately NOT saved.
 */
public class ChunkPortalData extends SavedData {
    private static final String NAME = "thevillagefightsback_chunk_portals";

    private boolean activated = false;
    private final Set<Long> portals = new HashSet<>();    // overworld chunk keys turned into chunk portals
    private final Set<Long> limboRings = new HashSet<>(); // limbo chunk keys whose return ring has been built
    /** Transient: portal chunk key -> next game-time it may emerge a creature. Re-seeded lazily each session. */
    private final Map<Long, Long> nextSpawn = new HashMap<>();

    public ChunkPortalData() {}

    public static ChunkPortalData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(ChunkPortalData::load, ChunkPortalData::new, NAME);
    }

    public boolean isActivated() { return activated; }
    public void setActivated(boolean v) { if (activated != v) { activated = v; setDirty(); } }

    public Set<Long> portals() { return portals; }
    public boolean isPortal(long key) { return portals.contains(key); }
    public void addPortal(long key) { if (portals.add(key)) setDirty(); }

    public boolean isLimboRing(long key) { return limboRings.contains(key); }
    public void addLimboRing(long key) { if (limboRings.add(key)) setDirty(); }

    /** Live (unsaved) map of portal key -> next emergence game-time. */
    public Map<Long, Long> nextSpawn() { return nextSpawn; }

    public static ChunkPortalData load(CompoundTag tag) {
        ChunkPortalData d = new ChunkPortalData();
        d.activated = tag.getBoolean("activated");
        for (long k : tag.getLongArray("portals")) d.portals.add(k);
        for (long k : tag.getLongArray("limboRings")) d.limboRings.add(k);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("activated", activated);
        tag.putLongArray("portals", portals.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("limboRings", limboRings.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
