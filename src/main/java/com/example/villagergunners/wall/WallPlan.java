package com.example.villagergunners.wall;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A fully computed, deterministic wall plan for one village (owned by one Gravemind). The block list is
 * sorted course-first so building proceeds layer by layer; completion is judged purely by comparing the
 * world to the plan (so a destroyed wall block is automatically rebuilt). Reservations (who is building
 * what right now) live in {@link WallBuild}, not here.
 */
public final class WallPlan {
    /** Number of vertical courses (0..4 for the curtain; corner pillars also fill course 5). */
    public static final int COURSES = 6;

    /** Margin (blocks) added to the wall's outer radius so a builder anywhere on/near its wall keeps the plan. */
    private static final double JOIN_MARGIN = 48.0;

    private final GlobalPos gravemind;
    private final List<List<WallBlock>> columns; // perimeter-ordered columns (each a course stack); for the conga
    private final List<WallBlock> blocks;        // flat, sorted by (course, x, z, type); for completion/repair
    private final int[] courseStart;             // courseStart[c]..courseStart[c+1] = blocks of course c
    private final double joinRadiusSqr;          // (max block distance from gravemind + margin)^2, for plan lookup
    private final List<BlockPos> scaffoldTemp = new ArrayList<>();
    private boolean complete;
    private long startTick;                      // gametime the plan was laid out

    // Transient build cursor: the lowest course that still has unbuilt blocks. Only advances forward.
    private transient int cursorCourse;

    public WallPlan(GlobalPos gravemind, List<List<WallBlock>> columns) {
        this.gravemind = gravemind;
        this.columns = columns;
        List<WallBlock> flat = new ArrayList<>();
        for (List<WallBlock> col : columns) flat.addAll(col);
        flat.sort(java.util.Comparator
                .comparingInt(WallBlock::course)
                .thenComparingInt(b -> b.pos().getX())
                .thenComparingInt(b -> b.pos().getZ())
                .thenComparingInt(b -> b.type().ordinal()));
        this.blocks = flat;
        this.courseStart = computeCourseStarts(flat);
        // The wall can sit far from the gravemind (village centre) on a big/elongated village; size the
        // builder join radius to the actual wall extent so far-side builders don't "lose" the plan.
        double maxR2 = 0;
        for (WallBlock wb : flat) maxR2 = Math.max(maxR2, gravemind.pos().distSqr(wb.pos()));
        double r = Math.sqrt(maxR2) + JOIN_MARGIN;
        this.joinRadiusSqr = r * r;
    }

    /** Radius (squared) from the gravemind within which a builder is considered "at this wall". */
    public double joinRadiusSqr() { return joinRadiusSqr; }

    private static int[] computeCourseStarts(List<WallBlock> blocks) {
        int[] starts = new int[COURSES + 1];
        int idx = 0;
        for (int c = 0; c <= COURSES; c++) {
            while (idx < blocks.size() && blocks.get(idx).course() < c) idx++;
            starts[c] = idx;
        }
        starts[COURSES] = blocks.size();
        return starts;
    }

    public GlobalPos gravemind() { return gravemind; }
    public List<WallBlock> blocks() { return blocks; }
    public List<BlockPos> scaffoldTemp() { return scaffoldTemp; }
    public boolean isComplete() { return complete; }
    public void markComplete() { this.complete = true; }

    /** Distance squared from a position to the village centre (the gravemind). */
    public double distSqrTo(BlockPos p) {
        return gravemind.pos().distSqr(p);
    }

    public boolean isBuilt(Level level, WallBlock wb) {
        return wb.matches(level.getBlockState(wb.pos()));
    }

    /**
     * A placement we consider "done" for build/completion purposes: either already built, or blocked by a
     * non-replaceable obstacle we refuse to bulldoze (a house wall, bedrock, ...). Blocked cells are left as
     * gaps so builders don't spin on them forever and the wall can still complete.
     */
    public boolean satisfied(Level level, WallBlock wb) {
        BlockState world = level.getBlockState(wb.pos());
        if (wb.matches(world)) return true;             // already built
        if (world.canBeReplaced()) return false;         // empty/replaceable -> still to build
        float hardness = world.getDestroySpeed(level, wb.pos());
        return hardness < 0 || hardness >= 25.0F;        // too tough to dig -> leave a gap (counts as done)
    }

    /**
     * The lowest course index (0..4) that still contains an unbuilt block, or {@link #COURSES} (5) if the
     * whole wall is built. Advances and caches the cursor so finished courses are never rescanned.
     */
    public int lowestIncompleteCourse(Level level) {
        while (cursorCourse < COURSES) {
            boolean allDone = true;
            for (int i = courseStart[cursorCourse]; i < courseStart[cursorCourse + 1]; i++) {
                if (!satisfied(level, blocks.get(i))) { allDone = false; break; }
            }
            if (allDone) cursorCourse++; else break;
        }
        return cursorCourse;
    }

    /**
     * Pick the build target for a mob standing near {@code near}: the unbuilt, unreserved block of the
     * lowest incomplete course that is nearest to the mob. Returns null if nothing is currently available
     * (whole wall built, or every remaining block in the lowest course is reserved).
     */
    public WallBlock nextUnbuiltFor(Level level, BlockPos near, Predicate<BlockPos> reserved) {
        int c = lowestIncompleteCourse(level);
        if (c >= COURSES) return null;
        WallBlock best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = courseStart[c]; i < courseStart[c + 1]; i++) {
            WallBlock wb = blocks.get(i);
            if (satisfied(level, wb) || reserved.test(wb.pos())) continue;
            double d = wb.pos().distSqr(near);
            if (d < bestD) { bestD = d; best = wb; }
        }
        return best;
    }

    public long startTick() { return startTick; }
    public void setStartTick(long t) { this.startTick = t; }
    public int columnCount() { return columns.size(); }

    /**
     * Conga selection: the first not-yet-built, supported block of {@code course}, scanning the perimeter
     * columns from {@code fromColumn} (wrapping). Giving each builder a different start column spreads the
     * six of them around the ring instead of all piling onto the same first cell.
     */
    public WallBlock nextForCourse(Level level, int course, int fromColumn, Predicate<BlockPos> skip) {
        int n = columns.size();
        if (n == 0) return null;
        int start = Math.floorMod(fromColumn, n);
        for (int k = 0; k < n; k++) {
            for (WallBlock wb : columns.get((start + k) % n)) {
                // cheap filters (course, skip/reserved) before the world lookups in satisfied()/hasSupport()
                if (wb.course() != course || skip.test(wb.pos())) continue;
                if (!satisfied(level, wb) && hasSupport(level, wb)) return wb;
            }
        }
        return null;
    }

    /**
     * A course block is buildable only once there is something to stand/build on directly below it (the
     * course beneath already built, or the ground for course 0). This makes each layer follow the one under
     * it -- the "reuse the previous layer" conga -- and stops upper-course builders from trying to pillar up
     * to an unbuilt mid-air column (which they couldn't reach, so they used to abandon and skip the whole
     * layer). Ladders/torches attach to the curtain's face rather than stacking, so they are never gated.
     */
    private boolean hasSupport(Level level, WallBlock wb) {
        if (wb.course() == 0) return true;
        if (wb.type() == WallBlock.BlockType.LADDER || wb.type() == WallBlock.BlockType.TORCH) return true;
        return !level.getBlockState(wb.pos().below()).canBeReplaced(); // course below must already be there
    }

    /** Helper selection for a builder whose own layer has no ready cell: the lowest unfinished layer's next
     *  block, still starting the perimeter scan at {@code fromColumn} so helpers stay spread out. */
    public WallBlock nextAnyCourse(Level level, int fromColumn, Predicate<BlockPos> skip) {
        for (int c = 0; c < COURSES; c++) {
            WallBlock t = nextForCourse(level, c, fromColumn, skip);
            if (t != null) return t;
        }
        return null;
    }

    // Lazy index (packed position -> course) for O(1) repair lookups; rebuilt on first use / after load.
    private transient java.util.Map<Long, Integer> posCourse;

    /**
     * Mark the wall block at {@code pos} as needing rebuild: clear completion and rewind the build cursor
     * to that block's course, so the existing builders re-place exactly that block (already-built blocks in
     * the course are skipped via {@code satisfied}). Returns false (no-op) if {@code pos} isn't part of this
     * wall. O(1) after a one-time lazy index -- no world scan, called only on an actual break event.
     */
    public boolean reopenAt(BlockPos pos) {
        if (posCourse == null) {
            posCourse = new java.util.HashMap<>(blocks.size() * 2);
            for (WallBlock wb : blocks) posCourse.put(wb.pos().asLong(), wb.course());
        }
        Integer c = posCourse.get(pos.asLong());
        if (c == null) return false;
        complete = false;
        if (c < cursorCourse) cursorCourse = c;
        return true;
    }

    private transient int scanIndex; // round-robin cursor for amortized damage scanning

    /**
     * Source-agnostic, amortized damage check for a COMPLETED wall: verify up to {@code window} blocks per
     * call (round-robin across calls, so cost is O(window) regardless of wall size), and reopen any that are
     * now a replaceable gap (i.e. a wall block was destroyed by ANY cause -- player, creeper, or a mob/alien
     * that bypasses block-break events). Skips positions in unloaded chunks (no force-load, no false hits).
     * Returns true if any damage was found. Pre-existing solid obstacles (house walls left as gaps) are
     * left alone, since {@code satisfied} treats non-replaceable blocks as fine.
     */
    public boolean scanForDamage(ServerLevel level, int window) {
        if (!complete || blocks.isEmpty()) return false;
        boolean damaged = false;
        int n = Math.min(window, blocks.size());
        for (int i = 0; i < n; i++) {
            if (scanIndex >= blocks.size()) scanIndex = 0;
            WallBlock wb = blocks.get(scanIndex++);
            if (!level.isLoaded(wb.pos())) continue;       // don't force-load / false-detect unloaded parts
            if (!satisfied(level, wb)) {                    // became air/replaceable where wall should be
                complete = false;
                if (wb.course() < cursorCourse) cursorCourse = wb.course();
                damaged = true;
            }
        }
        return damaged;
    }

    /** Re-evaluate completion (cheap, cursor-based). Returns true if the wall just became complete. */
    public boolean recheckCompletion(ServerLevel level) {
        if (complete) return false;
        if (lowestIncompleteCourse(level) >= COURSES) {
            complete = true;
            return true;
        }
        return false;
    }

    public void addScaffold(BlockPos pos) {
        scaffoldTemp.add(pos.immutable());
    }

    // --- persistence (stored inside GravemindData) ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("gm_dim", gravemind.dimension().location().toString());
        tag.putLong("gm_pos", gravemind.pos().asLong());
        tag.putBoolean("complete", complete);
        tag.putLong("startTick", startTick);
        ListTag list = new ListTag();
        for (int ci = 0; ci < columns.size(); ci++) {
            for (WallBlock wb : columns.get(ci)) {
                CompoundTag c = new CompoundTag();
                c.putLong("p", wb.pos().asLong());
                c.putByte("t", (byte) wb.type().ordinal());
                c.putByte("f", (byte) (wb.facing() == null ? -1 : wb.facing().get3DDataValue()));
                c.putBoolean("h", wb.topHalf());
                c.putByte("c", (byte) wb.course());
                c.putInt("ci", ci); // perimeter column index, to rebuild the column order on load
                list.add(c);
            }
        }
        tag.put("blocks", list);
        long[] scaf = new long[scaffoldTemp.size()];
        for (int i = 0; i < scaf.length; i++) scaf[i] = scaffoldTemp.get(i).asLong();
        tag.putLongArray("scaffold", scaf);
        return tag;
    }

    public static WallPlan load(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(
                Registries.DIMENSION, new ResourceLocation(tag.getString("gm_dim")));
        GlobalPos gm = GlobalPos.of(dim, BlockPos.of(tag.getLong("gm_pos")));
        java.util.TreeMap<Integer, List<WallBlock>> byColumn = new java.util.TreeMap<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            byte f = c.getByte("f");
            Direction facing = f < 0 ? null : Direction.from3DDataValue(f);
            WallBlock wb = new WallBlock(
                    BlockPos.of(c.getLong("p")),
                    WallBlock.BlockType.values()[c.getByte("t")],
                    facing,
                    c.getBoolean("h"),
                    c.getByte("c"));
            byColumn.computeIfAbsent(c.getInt("ci"), k -> new ArrayList<>()).add(wb);
        }
        WallPlan plan = new WallPlan(gm, new ArrayList<>(byColumn.values())); // TreeMap -> column order preserved
        plan.complete = tag.getBoolean("complete");
        plan.startTick = tag.getLong("startTick");
        for (long l : tag.getLongArray("scaffold")) plan.scaffoldTemp.add(BlockPos.of(l));
        return plan;
    }
}
