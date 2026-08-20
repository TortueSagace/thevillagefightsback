package com.example.villagefightsback.infection;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * The village's PROTECTED VOLUMES: the exact axis-aligned box of every structure the village has built
 * (houses, towers, golem factory, barracks, lure tower). Block infection -- both the brain node's fertile
 * spread and the emerald faction's infested paths -- is refused inside these boxes, so a village never
 * digests its own buildings.
 *
 * <h2>Why a chunk-bucketed index and not a list</h2>
 * The infection engine walks up to {@code MAX_CELLS_PER_PASS} (8192) frontier cells every 5 ticks, and each
 * cell probes its 26 neighbours, so {@link #contains} can be asked millions of times a minute. A linear scan
 * over every building in the world would be exactly the lag this has to avoid -- and it would get worse as the
 * village grows, which is the wrong direction (mega_expansion_mode founds a building every minute).
 *
 * <p>So boxes are bucketed by chunk. A query is:
 * <ol>
 *   <li>one {@code isEmpty()} check -- a world with no buildings yet pays literally nothing;</li>
 *   <li>one primitive-keyed hash lookup (fastutil, so no {@link Long} boxing and no allocation per query),
 *       which misses for every chunk that holds no building -- the overwhelming majority;</li>
 *   <li>only on a hit, a scan of that chunk's few boxes (a house spans 1-4 chunks, so this is ~1-2 boxes)
 *       over a flat {@code int[]} of 6-tuples, which is cache-friendly and allocation-free.</li>
 * </ol>
 * Cost is therefore independent of how many buildings exist anywhere else in the world.
 *
 * <p>Y is tested FIRST inside a bucket: the chunk lookup has already established that x/z are close, so the
 * thin vertical band a building occupies is the discriminator that rejects fastest.
 */
public class StructureZones extends SavedData {
    private static final String NAME = "thevillagefightsback_structure_zones";

    /** Ints per box in the flat arrays: minX, minY, minZ, maxX, maxY, maxZ (all inclusive). */
    private static final int STRIDE = 6;

    /** The canonical boxes, in registration order. This is what gets saved; {@link #byChunk} is derived. */
    private final List<int[]> boxes = new ArrayList<>();

    /** chunk key ({@link ChunkPos#asLong}) -> flat run of 6-tuples for the boxes overlapping that chunk. */
    private final Long2ObjectMap<int[]> byChunk = new Long2ObjectOpenHashMap<>();

    public static StructureZones get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StructureZones::load, StructureZones::new, NAME);
    }

    /**
     * Every protected building whose box lies within {@code radius} of (x, z), as {minX,minY,minZ,maxX,maxY,maxZ}.
     *
     * <p>Cold path: the wall survey calls this once per plan, not per block, so it walks the canonical list
     * rather than the chunk index. The village's own buildings have to be part of the footprint a wall is
     * planned around -- see BrainNodeEvents.collectVillagePoints.
     */
    public java.util.List<int[]> boxesNear(int x, int z, int radius) {
        java.util.List<int[]> out = new ArrayList<>();
        for (int[] b : boxes) {
            if (b[3] < x - radius || b[0] > x + radius) continue;
            if (b[5] < z - radius || b[2] > z + radius) continue;
            out.add(b);
        }
        return out;
    }

    /** True when nothing is protected in this dimension, so callers can skip the query entirely. */
    public boolean isEmpty() {
        return byChunk.isEmpty();
    }

    /** How many structures are registered (for the status report / diagnostics). */
    public int count() {
        return boxes.size();
    }

    /**
     * Is {@code (x, y, z)} inside a protected structure box? The hot path -- see the class note for why this
     * shape. Deliberately takes raw ints so a caller sweeping neighbours never has to allocate a BlockPos.
     */
    public boolean contains(int x, int y, int z) {
        if (byChunk.isEmpty()) return false;
        int[] run = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (run == null) return false;
        for (int i = 0; i < run.length; i += STRIDE) {
            // Y first: the chunk bucket already implies x/z are near, so the building's thin Y band rejects fastest.
            if (y < run[i + 1] || y > run[i + 4]) continue;
            if (x < run[i] || x > run[i + 3]) continue;
            if (z < run[i + 2] || z > run[i + 5]) continue;
            return true;
        }
        return false;
    }

    /**
     * Does the box {@code (minX..maxZ)} overlap a protected structure that is not itself?
     *
     * <p>Asked by {@code HousePlacer} before it builds. A build carves its whole footprint to air, and until
     * this existed it consulted nothing -- so a house dropped next to another house simply deleted the
     * neighbour, which is the single most destructive thing the mod could do to a village it had just spent
     * an hour raising.
     *
     * <p>"Not itself" is load-bearing. {@link #addBox} deliberately drops an exact duplicate so a rebuild on
     * the same footprint works; a byte-identical box must therefore read as NOT foreign here, or that rebuild
     * would be refused forever instead.
     *
     * <p>Cold path -- once per build, never per block -- so it walks the chunk span plainly.
     */
    public boolean intersectsForeign(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (byChunk.isEmpty()) return false;
        int x0 = Math.min(minX, maxX), y0 = Math.min(minY, maxY), z0 = Math.min(minZ, maxZ);
        int x1 = Math.max(minX, maxX), y1 = Math.max(minY, maxY), z1 = Math.max(minZ, maxZ);
        for (int cx = x0 >> 4; cx <= (x1 >> 4); cx++) {
            for (int cz = z0 >> 4; cz <= (z1 >> 4); cz++) {
                int[] run = byChunk.get(ChunkPos.asLong(cx, cz));
                if (run == null) continue;
                for (int i = 0; i < run.length; i += STRIDE) {
                    if (run[i] == x0 && run[i + 1] == y0 && run[i + 2] == z0
                            && run[i + 3] == x1 && run[i + 4] == y1 && run[i + 5] == z1) {
                        continue;   // this very footprint: a rebuild, not a neighbour
                    }
                    if (x1 < run[i] || x0 > run[i + 3]) continue;
                    if (y1 < run[i + 1] || y0 > run[i + 4]) continue;
                    if (z1 < run[i + 2] || z0 > run[i + 5]) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /** Convenience for the cold call sites that only have a BlockPos. */
    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * One-liner for the COLD write sites that spread on their own clock rather than through the infection
     * engine's per-pass walk -- the fertile grass / chorus random ticks, the sculk bloom, the virus rot. Those
     * fire rarely per block, so the extra SavedData lookup is irrelevant there; the engine's hot walk threads a
     * {@link StructureZones} instance down instead so it never repeats the lookup.
     */
    public static boolean protectedAt(ServerLevel level, BlockPos pos) {
        return get(level).contains(pos);
    }

    /**
     * Register a structure's footprint. Bounds are INCLUSIVE. Called once per building, so this side may be
     * as slow as it likes; it fans the box out into every chunk bucket it overlaps.
     *
     * <p>Overlapping boxes are harmless (a containment test does not care), but an exact duplicate is dropped so
     * rebuilding on the same spot cannot grow the index without bound.
     */
    public void addBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int[] box = {Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                     Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ)};
        for (int[] existing : boxes) {
            if (existing[0] == box[0] && existing[1] == box[1] && existing[2] == box[2]
                    && existing[3] == box[3] && existing[4] == box[4] && existing[5] == box[5]) {
                return; // already protected -- a rebuild on the same footprint
            }
        }
        boxes.add(box);
        index(box);
        setDirty();
    }

    /** Append {@code box} to the bucket of every chunk it overlaps. */
    private void index(int[] box) {
        int cx0 = box[0] >> 4, cx1 = box[3] >> 4;
        int cz0 = box[2] >> 4, cz1 = box[5] >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                int[] run = byChunk.get(key);
                if (run == null) {
                    byChunk.put(key, box.clone());
                    continue;
                }
                int[] grown = new int[run.length + STRIDE];
                System.arraycopy(run, 0, grown, 0, run.length);
                System.arraycopy(box, 0, grown, run.length, STRIDE);
                byChunk.put(key, grown);
            }
        }
    }

    // --- persistence ---------------------------------------------------------------------------------
    // Saved as one flat int array (6 per box) rather than a list of compounds: far smaller on disk and it
    // rebuilds the chunk index in one pass on load.

    @Override
    public CompoundTag save(CompoundTag tag) {
        int[] flat = new int[boxes.size() * STRIDE];
        int i = 0;
        for (int[] box : boxes) {
            System.arraycopy(box, 0, flat, i, STRIDE);
            i += STRIDE;
        }
        tag.putIntArray("Boxes", flat);
        return tag;
    }

    public static StructureZones load(CompoundTag tag) {
        StructureZones data = new StructureZones();
        int[] flat = tag.getIntArray("Boxes");
        for (int i = 0; i + STRIDE <= flat.length; i += STRIDE) {
            int[] box = new int[STRIDE];
            System.arraycopy(flat, i, box, 0, STRIDE);
            data.boxes.add(box);
            data.index(box);
        }
        return data;
    }
}
