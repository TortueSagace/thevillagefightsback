package com.example.villagefightsback.infection;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension infection state (persisted). Kept deliberately small/bounded so it scales to huge converted
 * zones without lag:
 *  - {@link #pathHeads}: the <=100 most-recent path blocks (only these grow new path -- spec point 1).
 *  - {@link #frontier}: infection blocks that may still convert a neighbour, processed round-robin under a
 *    per-tick budget. Interior blocks retire out of it, so its size tracks the active perimeter, not area.
 *  - {@link #rich}: positions of rich blocks, so miners find them without scanning the world.
 *  - {@link #boosts}: local "feels attacked" speed-up zones (centre + expiry); stack, capped.
 * Everything is stored as packed {@code BlockPos.asLong()}.
 */
public class InfectionData extends SavedData {
    // NOTE: there used to be a 5x "first 10 seconds" window here (fastUntil / setFastUntil / isFast). It had
    // no callers left -- nothing ever turned it on -- so it was removed rather than left as a switch that
    // looked like it governed the infection rate and did not. Old saves may still carry a "fastUntil" key;
    // it is simply ignored.
    private static final String NAME = "thevillagefightsback_infection";

    public static final int PATH_HEAD_CAP = 100;     // only the 100 newest path blocks can grow (spec point 1)
    public static final int FRONTIER_CAP = 60_000;   // hard memory bound on the active conversion frontier
    public static final int RICH_CAP = 20_000;       // hard memory bound on tracked rich blocks
    public static final double BOOST_RADIUS = 6.0;   // "neighborhood" of a lost block that speeds up
    public static final int MAX_BOOST_STACKS = 19;   // +1900% cap => multiplier up to 20x

    // --- path growth heads (bounded FIFO) ---
    private final ArrayDeque<Long> pathHeads = new ArrayDeque<>();
    private final Set<Long> pathHeadSet = new HashSet<>();

    // --- conversion frontier (round-robin queue + membership set) ---
    private final ArrayDeque<Long> frontier = new ArrayDeque<>();
    private final Set<Long> frontierSet = new HashSet<>();

    // --- rich blocks (for miners) ---
    private final LinkedHashSet<Long> rich = new LinkedHashSet<>();

    // --- local attack speed-up zones ---
    private final ArrayDeque<long[]> boosts = new ArrayDeque<>(); // [centerPos, expiryGameTime]

    // --- which brainNodes have already been seeded (so we seed once per village) ---
    private final Set<Long> seeded = new HashSet<>();

    // --- infested path blocks (v1.0.3): slow, full-radius proliferation sources placed by emerald conversion.
    //
    // At most INFESTED_CAP are ACTIVE at once, which is what bounds the per-pass range scan in
    // InfectionManager.convert(). The rest queue up in `waiting` and take their turn: when an active path has
    // nothing left to convert in its radius, InfectionManager retires it and promotes the head of the queue.
    //
    // It used to be a plain FIFO that DROPPED the oldest on overflow, so in a heavily infested world only the 64
    // most recent paths ever did anything and every older one was inert for good -- the player sees dozens of
    // infested paths and a handful of them spreading. Nothing is discarded now until `waiting` itself overflows,
    // which takes WAITING_CAP paths in one dimension.
    public static final int INFESTED_CAP = 64;      // active at once
    public static final int WAITING_CAP = 4096;     // queued behind them; bounds the save file, not the tick
    private final ArrayDeque<Long> infested = new ArrayDeque<>();
    private final Set<Long> infestedSet = new HashSet<>();
    private final ArrayDeque<Long> waiting = new ArrayDeque<>();
    private final Set<Long> waitingSet = new HashSet<>();


    // Running tally of "fertilized" blocks (our fertile/rich/shaped/door blocks only -- NOT dirt paths), shown
    // in the brain node UI. There is no longer a hard cap; the spread is bounded spatially by brainNode range.
    private int converted = 0;

    // transient: positions whose removal was sanctioned (player break / miner dig) -> not an "attack"
    private final Map<Long, Long> sanctioned = new HashMap<>();

    public InfectionData() {}

    public static InfectionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(InfectionData::load, InfectionData::new, NAME);
    }

    // --- path heads ---
    public boolean hasSeeded(long brainNode) { return seeded.contains(brainNode); }
    public void markSeeded(long brainNode) { if (seeded.add(brainNode)) setDirty(); }

    /** Register a freshly-created path block: it becomes a growth head (evicting the oldest past the cap) and
     *  joins the conversion frontier. The evicted head stays in the world; it just stops growing new path. */
    public void addPathHead(long pos) {
        if (pathHeadSet.add(pos)) {
            pathHeads.addLast(pos);
            while (pathHeads.size() > PATH_HEAD_CAP) {
                Long old = pathHeads.pollFirst();
                if (old != null) pathHeadSet.remove(old);
            }
        }
        addFrontier(pos);
        setDirty();
    }

    public Iterable<Long> pathHeadsSnapshot() {
        return new ArrayDeque<>(pathHeads); // copy so callers can mutate during iteration
    }

    public boolean isPathHead(long pos) { return pathHeadSet.contains(pos); }

    public void dropPathHead(long pos) {
        if (pathHeadSet.remove(pos)) { pathHeads.remove(pos); setDirty(); }
    }

    // --- infested path proliferation sources ---
    /** Register a freshly-placed infested path block: it becomes a slow proliferation centre and seeds the
     *  conversion frontier so fertile/rich blocks start spreading from it. */
    public void addInfestedPath(long pos) {
        if (!infestedSet.contains(pos) && !waitingSet.contains(pos)) {
            if (infested.size() < INFESTED_CAP) {
                infested.addLast(pos);
                infestedSet.add(pos);
            } else {
                waiting.addLast(pos);
                waitingSet.add(pos);
                while (waiting.size() > WAITING_CAP) {          // only here does a path lose its turn for good
                    Long old = waiting.pollFirst();
                    if (old != null) waitingSet.remove(old);
                }
            }
            setDirty();
        }
        addFrontier(pos); // start converting the ground around it
    }

    public Iterable<Long> infestedSnapshot() {
        return new ArrayDeque<>(infested); // copy so callers can iterate safely
    }

    /** The active paths, in order, as a primitive array -- the per-pass form InfectionManager wants. */
    public long[] activeInfested() {
        long[] out = new long[infested.size()];
        int i = 0;
        for (long l : infested) out[i++] = l;
        return out;
    }

    /** How many paths are queued behind the active set. */
    public int waitingInfestedCount() {
        return waiting.size();
    }

    /**
     * An active path has finished converting everything it can reach: drop it from the active set and promote
     * the longest-waiting path in its place.
     *
     * <p>The retired path stays in the world -- it is a block, and the fertile ground it made stays fertile. It
     * simply stops contributing a proliferation radius, having nothing left to contribute.
     *
     * @return the promoted path, or {@code null} if nothing was waiting (the retirement still happens).
     */
    public Long retireInfestedPath(long pos) {
        if (!infestedSet.remove(pos)) return null;
        infested.remove(pos);
        Long next = waiting.pollFirst();
        if (next != null) {
            waitingSet.remove(next);
            infested.addLast(next);
            infestedSet.add(next);
            addFrontier(next);   // it has been idle in the queue; give it a frontier cell to work from
        }
        setDirty();
        return next;
    }

    // --- frontier ---
    public void addFrontier(long pos) {
        if (frontierSet.size() >= FRONTIER_CAP) return;       // saturate: bound memory
        if (frontierSet.add(pos)) { frontier.addLast(pos); setDirty(); }
    }

    public int frontierSize() { return frontier.size(); }

    // --- fertilized-block tally (display only). Counts ONLY fertile/rich/shaped/door blocks (not dirt paths). ---
    public void incConverted() { converted++; setDirty(); }
    public void decConverted() { if (converted > 0) { converted--; setDirty(); } }
    public int convertedCount() { return converted; }

    /** Pop the next frontier position to process (round-robin), or null. Caller must {@link #reactivate} or
     *  {@link #retire} it. */
    public Long pollFrontier() { return frontier.pollFirst(); }

    public void reactivate(long pos) { frontier.addLast(pos); } // still has work -> back of the queue

    public void retire(long pos) { frontierSet.remove(pos); }   // no more work here

    // --- rich blocks ---
    public void addRich(long pos) {
        if (rich.size() >= RICH_CAP) return;
        if (rich.add(pos)) setDirty();
    }

    public void removeRich(long pos) { if (rich.remove(pos)) setDirty(); }

    /** A rich block within {@code range} of {@code from} (nearest; bounded scan), or null. */
    public BlockPos nearestRich(BlockPos from, double range) {
        return nearestRich(from, range, null);
    }

    /** As {@link #nearestRich(BlockPos, double)} but skipping any position in {@code exclude} (e.g. a miner's
     *  blacklist of rich blocks it couldn't reach), so a different reachable block is chosen instead. */
    public BlockPos nearestRich(BlockPos from, double range, Set<Long> exclude) {
        double r2 = range * range;
        int scanned = 0;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (long l : rich) {
            if (++scanned > 2048) break;               // bound the per-call cost on huge sets
            if (exclude != null && exclude.contains(l)) continue;
            BlockPos p = BlockPos.of(l);
            double d = p.distSqr(from);
            if (d <= r2 && d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    // --- attack boosts ---
    public void addBoost(long center, long expiry) {
        boosts.addLast(new long[]{center, expiry});
        setDirty();
    }

    /** Local infection multiplier at {@code pos}: 1 + (overlapping active boosts), capped at 1+MAX_BOOST_STACKS. */
    public double multiplierAt(BlockPos pos, long now) {
        int stacks = 0;
        for (long[] b : boosts) {
            if (b[1] <= now) continue;
            if (BlockPos.of(b[0]).distSqr(pos) <= BOOST_RADIUS * BOOST_RADIUS) stacks++;
        }
        if (stacks > MAX_BOOST_STACKS) stacks = MAX_BOOST_STACKS;
        return 1.0 + stacks;
    }

    public void pruneBoosts(long now) {
        boolean changed = false;
        while (!boosts.isEmpty() && boosts.peekFirst()[1] <= now) { boosts.pollFirst(); changed = true; }
        if (changed) setDirty();
    }

    // --- sanctioned removals (transient) ---
    public void sanction(long pos, long now) { sanctioned.put(pos, now + 100L); } // ~5s grace
    public boolean wasSanctioned(long pos, long now) {
        Long exp = sanctioned.get(pos);
        if (exp == null) return false;
        if (exp <= now) { sanctioned.remove(pos); return false; }
        return true;
    }
    public void cleanupSanctioned(long now) { sanctioned.values().removeIf(e -> e <= now); }

    // --- persistence ---
    private static long[] toArray(java.util.Collection<Long> c) {
        long[] a = new long[c.size()];
        int i = 0;
        for (long l : c) a[i++] = l;
        return a;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("pathHeads", toArray(pathHeads));
        tag.putLongArray("frontier", toArray(frontier));
        tag.putLongArray("rich", toArray(rich));
        tag.putLongArray("seeded", toArray(seeded));
        tag.putLongArray("infested", toArray(infested));
        tag.putLongArray("infestedWaiting", toArray(waiting));
        tag.putInt("convertedV3", converted); // V3: fertilized-only tally; older keys (path-inflated / stuck) ignored
        long[] bc = new long[boosts.size()], be = new long[boosts.size()];
        int i = 0;
        for (long[] b : boosts) { bc[i] = b[0]; be[i] = b[1]; i++; }
        tag.putLongArray("boostC", bc);
        tag.putLongArray("boostE", be);
        return tag;
    }

    public static InfectionData load(CompoundTag tag) {
        InfectionData d = new InfectionData();
        for (long l : tag.getLongArray("pathHeads")) { d.pathHeads.addLast(l); d.pathHeadSet.add(l); }
        for (long l : tag.getLongArray("frontier")) { d.frontier.addLast(l); d.frontierSet.add(l); }
        for (long l : tag.getLongArray("rich")) d.rich.add(l);
        for (long l : tag.getLongArray("seeded")) d.seeded.add(l);
        for (long l : tag.getLongArray("infested")) { d.infested.addLast(l); d.infestedSet.add(l); }
        for (long l : tag.getLongArray("infestedWaiting")) { d.waiting.addLast(l); d.waitingSet.add(l); }
        d.converted = tag.getInt("convertedV3"); // missing in old saves -> 0, so the stuck/inflated count resets
        long[] bc = tag.getLongArray("boostC"), be = tag.getLongArray("boostE");
        for (int i = 0; i < bc.length && i < be.length; i++) d.boosts.addLast(new long[]{bc[i], be[i]});
        return d;
    }
}
