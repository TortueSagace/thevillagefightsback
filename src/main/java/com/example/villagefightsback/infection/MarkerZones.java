package com.example.villagefightsback.infection;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * The player's own claims: every "Do not build here" sign, and the {@value #RADIUS}-block sphere around it the
 * village must leave alone. Settlers refuse to build inside one and block infection refuses to spread into one.
 *
 * <h2>Shaped like {@link StructureZones}, for the same reason</h2>
 * The infection engine asks {@link #blocked} from inside its 8192-cell-by-26-neighbour walk, so this has to be
 * as cheap as the protected-building index: an {@code isEmpty()} fast-out that costs nothing in a world with no
 * signs, then one primitive-keyed hash lookup that misses for every chunk holding no sign, then a short scan.
 *
 * <p>Unlike {@code StructureZones} this one can FORGET, because a sign is a block a player can break. Entries
 * are keyed on the sign's own position so {@link #remove} is exact.
 *
 * <h2>The standing rule</h2>
 * A sign only counts while the player who placed it is not an Enemy of the Village, so each entry remembers its
 * placer. Resolving a reputation per query would be absurd on that hot path, so {@link #refreshActive} recomputes
 * the whole active set once per infection pass and {@link #blocked} reads a plain set. A player who turns hostile
 * therefore loses every sign they own at the next pass, which is the intent -- and a player who is merely offline
 * loses nothing, because reputation is a global per-UUID record.
 */
public class MarkerZones extends SavedData {
    private static final String NAME = "thevillagefightsback_no_build_signs";

    /** How far a sign's protection reaches, in blocks. */
    public static final int RADIUS = 10;
    private static final int RADIUS_SQR = RADIUS * RADIUS;

    /** sign position -> the UUID of whoever placed it. The canonical record; {@link #byChunk} is derived. */
    private final Long2ObjectMap<UUID> signs = new Long2ObjectOpenHashMap<>();

    /** The subset of {@link #signs} whose placer currently counts. Recomputed, never saved. */
    private final LongSet active = new LongOpenHashSet();

    /** chunk key ({@link ChunkPos#asLong}) -> the sign positions whose radius reaches into that chunk. */
    private final Long2ObjectMap<long[]> byChunk = new Long2ObjectOpenHashMap<>();

    public static MarkerZones get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MarkerZones::load, MarkerZones::new, NAME);
    }

    /** True when no sign in this dimension is currently in force, so callers can skip the query entirely. */
    public boolean isEmpty() {
        return active.isEmpty();
    }

    /**
     * Is (x,y,z) inside the protected sphere of a sign that is currently in force?
     *
     * <p>HOT: called from the infection walk. Allocation-free apart from the {@link BlockPos} unpack, which only
     * happens on a chunk that actually holds a sign.
     */
    public boolean blocked(int x, int y, int z) {
        if (active.isEmpty()) return false;
        long[] run = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (run == null) return false;
        for (long key : run) {
            if (!active.contains(key)) continue;
            int sx = BlockPos.getX(key), sy = BlockPos.getY(key), sz = BlockPos.getZ(key);
            int dx = sx - x, dy = sy - y, dz = sz - z;
            if (dx * dx + dy * dy + dz * dz <= RADIUS_SQR) return true;
        }
        return false;
    }

    /** Does a sign's sphere overlap this box at all? Cold: asked once per attempted build. */
    public boolean blocksBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (active.isEmpty()) return false;
        for (int cx = (minX - RADIUS) >> 4; cx <= (maxX + RADIUS) >> 4; cx++) {
            for (int cz = (minZ - RADIUS) >> 4; cz <= (maxZ + RADIUS) >> 4; cz++) {
                long[] run = byChunk.get(ChunkPos.asLong(cx, cz));
                if (run == null) continue;
                for (long key : run) {
                    if (!active.contains(key)) continue;
                    int sx = BlockPos.getX(key), sy = BlockPos.getY(key), sz = BlockPos.getZ(key);
                    // Distance from the sign to the nearest point of the box, per axis.
                    int dx = sx < minX ? minX - sx : (sx > maxX ? sx - maxX : 0);
                    int dy = sy < minY ? minY - sy : (sy > maxY ? sy - maxY : 0);
                    int dz = sz < minZ ? minZ - sz : (sz > maxZ ? sz - maxZ : 0);
                    if (dx * dx + dy * dy + dz * dz <= RADIUS_SQR) return true;
                }
            }
        }
        return false;
    }

    /** Register a newly placed sign. A sign with no known placer never counts (see {@link #refreshActive}). */
    public void add(BlockPos pos, UUID placer) {
        long key = pos.asLong();
        if (signs.containsKey(key)) return;
        signs.put(key, placer);
        index(key);
        active.add(key);   // provisionally in force; the next refresh has the final word
        setDirty();
    }

    /** Forget a broken sign. */
    public void remove(BlockPos pos) {
        long key = pos.asLong();
        if (signs.remove(key) == null) return;
        active.remove(key);
        reindex();
        setDirty();
    }

    /** True if a sign is recorded at exactly this position (used to avoid re-adding on a block update). */
    public boolean has(BlockPos pos) {
        return signs.containsKey(pos.asLong());
    }

    /**
     * Recompute which signs are in force, from their placers' current standing. Called once per infection pass;
     * everything else reads the plain set this produces.
     */
    public void refreshActive(MinecraftServer server) {
        if (signs.isEmpty()) {
            if (!active.isEmpty()) active.clear();
            return;
        }
        com.example.villagefightsback.ReputationData rep = com.example.villagefightsback.ReputationData.get(server);
        active.clear();
        for (Long2ObjectMap.Entry<UUID> e : signs.long2ObjectEntrySet()) {
            UUID placer = e.getValue();
            if (placer == null) continue;                                    // unknown owner -> never in force
            if (rep.get(placer) <= com.example.villagefightsback.ReputationData.ENEMY_AT) continue;
            active.add(e.getLongKey());
        }
    }

    // --- index ---------------------------------------------------------------------------------------

    private void index(long key) {
        int x = BlockPos.getX(key), z = BlockPos.getZ(key);
        for (int cx = (x - RADIUS) >> 4; cx <= (x + RADIUS) >> 4; cx++) {
            for (int cz = (z - RADIUS) >> 4; cz <= (z + RADIUS) >> 4; cz++) {
                long ck = ChunkPos.asLong(cx, cz);
                long[] run = byChunk.get(ck);
                long[] grown = run == null ? new long[1] : java.util.Arrays.copyOf(run, run.length + 1);
                grown[grown.length - 1] = key;
                byChunk.put(ck, grown);
            }
        }
    }

    /** A removal cannot be patched out of the flat runs cheaply, so the index is simply rebuilt. Signs are few. */
    private void reindex() {
        byChunk.clear();
        for (long key : signs.keySet()) index(key);
    }

    // --- persistence ---------------------------------------------------------------------------------

    public static MarkerZones load(CompoundTag tag) {
        MarkerZones z = new MarkerZones();
        ListTag list = tag.getList("Signs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            long key = t.getLong("Pos");
            z.signs.put(key, t.hasUUID("Placer") ? t.getUUID("Placer") : null);
            z.index(key);
        }
        return z; // `active` is left empty on purpose: the first refresh decides what counts
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<UUID> e : signs.long2ObjectEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getLongKey());
            if (e.getValue() != null) t.putUUID("Placer", e.getValue());
            list.add(t);
        }
        tag.put("Signs", list);
        return tag;
    }
}
