package com.example.villagergunners.wall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Pure, deterministic geometry for the village wall. Turns a set of bed positions into the full ordered
 * list of {@link WallBlock}s the Gravemind hands to the builders. Strategy:
 * beds -> distinct (x,z) -> convex hull -> Minkowski "inflate by 5" (disc offset, then re-hull) ->
 * 4-connected rasterized perimeter -> per-column ground height (skipping floating islands) ->
 * expand each column into its 5-course block stack (loophole stairs every 5, ladders every 15,
 * battlements alternating). Everything is integer/array based and sorted, so the same input always yields
 * the identical plan.
 */
public final class WallPlanner {
    private WallPlanner() {}

    public static final int MIN_BEDS = 4;          // fewer distinct bed columns than this -> no wall yet
    private static final int FLOAT_GAP = 10;       // >= this much air below a surface => floating island, skip it
    private static final int SEGMENT = 5;          // band height -> length of the straight vertical wall runs
    private static final int MAX_COLUMNS = 600;     // perimeter cap (runaway-village guard)
    private static final int MAX_BLOCKS = 4000;     // total placement cap

    // Disc of radius 5 (the required bed clearance) sampled at 16 points -- precomputed so the offset is
    // fully deterministic with no runtime trig.
    private static final int[][] DISC = {
            {5, 0}, {5, 2}, {4, 4}, {2, 5}, {0, 5}, {-2, 5}, {-4, 4}, {-5, 2},
            {-5, 0}, {-5, -2}, {-4, -4}, {-2, -5}, {0, -5}, {2, -5}, {4, -4}, {5, -2}
    };

    /** Compute the wall plan, or null if there aren't enough beds / the village is too large. */
    public static WallPlan plan(ServerLevel level, GlobalPos gravemind, List<BlockPos> beds) {
        List<int[]> pts = distinctXz(beds);
        if (pts.size() < MIN_BEDS) return null;

        double[] centroid = centroid(pts);

        List<int[]> hull = convexHull(pts);
        if (hull.size() < 3) hull = boundingRect(pts);

        // Inflate the hull outward by CLEARANCE: drop a disc on every hull vertex, re-hull the samples.
        List<int[]> samples = new ArrayList<>(hull.size() * DISC.length);
        for (int[] v : hull) {
            for (int[] d : DISC) samples.add(new int[]{v[0] + d[0], v[1] + d[1]});
        }
        List<int[]> ring = convexHull(samples);
        if (ring.size() < 3) return null;

        // --- Rectilinear outline: turn the convex offset polygon into axis-aligned segments (no diagonals).
        // For each row z compute the polygon's [lo,hi] x-span, then quantize those extents into SEGMENT-tall
        // bands so the left/right walls are SEGMENT-long straight runs joined by horizontal jogs. The wall is
        // the boundary of that orthogonal region; corners (cells with two outside sides) become 6-tall pillars.
        int zMin = Integer.MAX_VALUE, zMax = Integer.MIN_VALUE;
        for (int[] v : ring) { zMin = Math.min(zMin, v[1]); zMax = Math.max(zMax, v[1]); }
        int rows = zMax - zMin + 1;
        int[] lo = new int[rows], hi = new int[rows];
        java.util.Arrays.fill(lo, Integer.MAX_VALUE);
        java.util.Arrays.fill(hi, Integer.MIN_VALUE);
        computeRowSpans(ring, zMin, lo, hi);
        for (int b = 0; b * SEGMENT < rows; b++) {
            int z0 = b * SEGMENT, z1 = Math.min(rows - 1, z0 + SEGMENT - 1);
            int bl = Integer.MAX_VALUE, bh = Integer.MIN_VALUE;
            for (int zi = z0; zi <= z1; zi++) if (lo[zi] <= hi[zi]) { bl = Math.min(bl, lo[zi]); bh = Math.max(bh, hi[zi]); }
            for (int zi = z0; zi <= z1; zi++) { lo[zi] = bl; hi[zi] = bh; }
        }

        // Collect boundary cells; outward = the outside side (a corner has >=2 outside sides -> pillar).
        List<Col> cells = new ArrayList<>();
        for (int zi = 0; zi < rows; zi++) {
            if (lo[zi] > hi[zi]) continue;
            int z = zMin + zi;
            for (int x = lo[zi]; x <= hi[zi]; x++) {
                boolean wN = !inRegion(lo, hi, zMin, x, z - 1);
                boolean wS = !inRegion(lo, hi, zMin, x, z + 1);
                boolean wE = !inRegion(lo, hi, zMin, x + 1, z);
                boolean wW = !inRegion(lo, hi, zMin, x - 1, z);
                int cnt = (wN ? 1 : 0) + (wS ? 1 : 0) + (wE ? 1 : 0) + (wW ? 1 : 0);
                if (cnt == 0) continue; // interior cell
                Direction o = cnt >= 2 ? null // corner -> pillar
                        : wN ? Direction.NORTH : wS ? Direction.SOUTH : wE ? Direction.EAST : Direction.WEST;
                cells.add(new Col(x, z, o));
            }
        }
        if (cells.isEmpty() || cells.size() > MAX_COLUMNS) return null;

        // Order the columns AROUND the village centre (by angle) so the per-layer builders all sweep the
        // perimeter the same way from the same start point.
        cells.sort(Comparator.comparingDouble(c -> Math.atan2(c.z - centroid[1], c.x - centroid[0])));

        // Gates: one 2-wide fake-cobblestone passage at the middle of each of the four sides.
        java.util.Set<Long> doors = pickDoors(cells, centroid);

        List<List<WallBlock>> columns = new ArrayList<>();
        int total = 0;
        int minY = level.getMinBuildHeight();
        for (Col col : cells) {
            if (!level.hasChunk(col.x >> 4, col.z >> 4)) continue;     // never build into unloaded terrain
            int surfaceY = surfaceY(level, col.x, col.z, minY);
            if (surfaceY <= minY) continue;                            // void column -> skip
            List<WallBlock> colBlocks = new ArrayList<>();
            expandColumn(col, surfaceY, colBlocks, doors.contains(pack(col.x, col.z)));
            if (!colBlocks.isEmpty()) { columns.add(colBlocks); total += colBlocks.size(); }
            if (total > MAX_BLOCKS) return null;
        }
        if (columns.isEmpty()) return null;
        return new WallPlan(gravemind, columns);
    }

    // --- column -> course stack ---

    private static void expandColumn(Col col, int surfaceY, List<WallBlock> out, boolean isDoor) {
        int base = surfaceY + 1;                 // wall sits ON the ground (one above the top solid block)

        // Corners (where two straight segments meet): a plain 6-tall cobblestone pillar, nothing else.
        if (col.outward == null) {
            for (int c = 0; c <= 5; c++) out.add(cobble(col.x, base + c, col.z, c));
            return;
        }

        Direction o = col.outward;
        Direction in = o.getOpposite();          // toward the village interior

        if (isDoor) {
            // A 3-high fake-cobblestone gate (courses 0..2) with a solid cobble cap above. We emit NO
            // loophole/ladder/torch/merlon on a door column, so nothing is ever placed ON the fake
            // cobblestone, and the opening stays a clean 2-wide x 3-high passage for an iron golem.
            out.add(fakeCobble(col.x, base, col.z, 0));
            out.add(fakeCobble(col.x, base + 1, col.z, 1));
            out.add(fakeCobble(col.x, base + 2, col.z, 2));
            out.add(cobble(col.x, base + 3, col.z, 3));
            out.add(cobble(col.x, base + 4, col.z, 4));
            return;
        }

        // Spacing runs ALONG the straight segment: vertical segments (outward E/W) along Z, horizontal (N/S)
        // along X. floorMod keeps it correct for negative coordinates.
        int coord = (o == Direction.EAST || o == Direction.WEST) ? col.z : col.x;
        boolean loophole = Math.floorMod(coord, 5) == 2;   // a firing slit every 5 blocks
        boolean ladder = Math.floorMod(coord, 15) == 0;    // a climb ladder every 15 blocks
        boolean merlon = Math.floorMod(coord, 2) == 0;     // battlements: alternating merlon / gap
        boolean torch = Math.floorMod(coord, 6) == 3 && !ladder; // light the inside face (not on ladder columns)

        // Stairs form the loophole. Their FACING must run ALONG the wall (parallel), not across it -- only
        // then does the L/Gamma notch leave an actual hole THROUGH the 1-thick wall to shoot from. (If the
        // slit ends up on the wrong side, flip this to o.getCounterClockWise().)
        Direction stairFacing = o.getClockWise();

        out.add(cobble(col.x, base, col.z, 0));
        out.add(loophole ? stair(col.x, base + 1, col.z, 1, stairFacing, false)
                         : cobble(col.x, base + 1, col.z, 1));
        out.add(loophole ? stair(col.x, base + 2, col.z, 2, stairFacing, true)
                         : cobble(col.x, base + 2, col.z, 2));
        out.add(cobble(col.x, base + 3, col.z, 3));
        if (merlon) out.add(cobble(col.x, base + 4, col.z, 4));

        if (ladder) {
            // Ladders climb the wall's INSIDE face: a ladder cell one block toward the interior, supported
            // by the cobble curtain behind it, facing the interior.
            BlockPos wall = new BlockPos(col.x, base, col.z);
            for (int c = 0; c <= 3; c++) {
                BlockPos lp = wall.relative(in).above(c);
                out.add(new WallBlock(lp, WallBlock.BlockType.LADDER, in, false, c));
            }
        }

        if (torch) {
            // A wall torch on the inside face at chest height, supported by the cobble curtain behind it.
            BlockPos tp = new BlockPos(col.x, base + 3, col.z).relative(in);
            out.add(new WallBlock(tp, WallBlock.BlockType.TORCH, in, false, 3));
        }
    }

    private static WallBlock cobble(int x, int y, int z, int course) {
        return new WallBlock(new BlockPos(x, y, z), WallBlock.BlockType.COBBLE, null, false, course);
    }

    private static WallBlock fakeCobble(int x, int y, int z, int course) {
        return new WallBlock(new BlockPos(x, y, z), WallBlock.BlockType.FAKE_COBBLE, null, false, course);
    }

    private static WallBlock stair(int x, int y, int z, int course, Direction facing, boolean topHalf) {
        return new WallBlock(new BlockPos(x, y, z), WallBlock.BlockType.STAIR, facing, topHalf, course);
    }

    // --- terrain height with the floating-island rule ---

    /**
     * The Y of the solid block the wall column rests on. Starts at the surface heightmap and, if that
     * surface has a big air gap ({@value #FLOAT_GAP}+) below it before the next solid (a floating island),
     * descends to the real ground. A normal surface with a cave under it has solid rock just below, so it
     * is kept (we do not dig into caves).
     */
    private static int surfaceY(LevelReader level, int x, int z, int minY) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        while (y > minY) {
            if (isSurface(level, x, y, z)) {
                int yy = y - 1;
                int gap = 0;
                while (yy > minY && !isSurface(level, x, yy, z)) { yy--; gap++; }
                if (gap >= FLOAT_GAP && yy > minY) { y = yy; continue; } // floating island -> descend
                return y;                                                // real ground
            }
            y--;
        }
        return y;
    }

    /**
     * A block that counts as the standable ground surface. Uses the heightmap's own "blocks motion"
     * notion (which includes partial ground blocks like dirt paths and farmland) rather than a full-face
     * sturdiness test -- otherwise a column over a dirt path would skip past the path to the dirt below it
     * and the wall would land inside the path block, leaving a hole.
     */
    private static boolean isSurface(LevelReader level, int x, int y, int z) {
        BlockState s = level.getBlockState(new BlockPos(x, y, z));
        return s.blocksMotion() || !s.getFluidState().isEmpty();
    }

    // --- 2D helpers (convex hull, offset, rasterization) ---

    private static List<int[]> distinctXz(List<BlockPos> beds) {
        List<int[]> pts = new ArrayList<>(beds.size());
        for (BlockPos b : beds) pts.add(new int[]{b.getX(), b.getZ()});
        pts.sort(Comparator.<int[]>comparingInt(a -> a[0]).thenComparingInt(a -> a[1]));
        List<int[]> out = new ArrayList<>(pts.size());
        for (int[] p : pts) {
            if (out.isEmpty() || out.get(out.size() - 1)[0] != p[0] || out.get(out.size() - 1)[1] != p[1]) {
                out.add(p);
            }
        }
        return out;
    }

    private static double[] centroid(List<int[]> pts) {
        double cx = 0, cz = 0;
        for (int[] p : pts) { cx += p[0]; cz += p[1]; }
        return new double[]{cx / pts.size(), cz / pts.size()};
    }

    /** Andrew's monotone chain. Input must be sorted+distinct. Returns hull vertices (>=0), collinear removed. */
    private static List<int[]> convexHull(List<int[]> ptsIn) {
        List<int[]> pts = distinctSorted(ptsIn);
        int n = pts.size();
        if (n < 3) return new ArrayList<>(pts);
        List<int[]> h = new ArrayList<>(2 * n);
        for (int[] p : pts) { // lower hull
            while (h.size() >= 2 && cross(h.get(h.size() - 2), h.get(h.size() - 1), p) <= 0) {
                h.remove(h.size() - 1);
            }
            h.add(p);
        }
        int lower = h.size() + 1;
        for (int i = n - 2; i >= 0; i--) { // upper hull
            int[] p = pts.get(i);
            while (h.size() >= lower && cross(h.get(h.size() - 2), h.get(h.size() - 1), p) <= 0) {
                h.remove(h.size() - 1);
            }
            h.add(p);
        }
        h.remove(h.size() - 1); // last == first
        return h;
    }

    private static List<int[]> distinctSorted(List<int[]> in) {
        List<int[]> pts = new ArrayList<>(in);
        pts.sort(Comparator.<int[]>comparingInt(a -> a[0]).thenComparingInt(a -> a[1]));
        List<int[]> out = new ArrayList<>(pts.size());
        for (int[] p : pts) {
            if (out.isEmpty() || out.get(out.size() - 1)[0] != p[0] || out.get(out.size() - 1)[1] != p[1]) {
                out.add(p);
            }
        }
        return out;
    }

    private static List<int[]> boundingRect(List<int[]> pts) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : pts) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]);
        }
        List<int[]> r = new ArrayList<>(4);
        r.add(new int[]{minX, minZ});
        r.add(new int[]{maxX, minZ});
        r.add(new int[]{maxX, maxZ});
        r.add(new int[]{minX, maxZ});
        return r;
    }

    private static long cross(int[] o, int[] a, int[] b) {
        return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** For each row z in [zMin, zMin+lo.length), the convex polygon's min/max x (its scanline span). */
    private static void computeRowSpans(List<int[]> ring, int zMin, int[] lo, int[] hi) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            int[] a = ring.get(i), b = ring.get((i + 1) % n);
            int az = a[1], bz = b[1], ax = a[0], bx = b[0];
            if (az == bz) { // horizontal edge: spans both endpoints at this z
                spanAt(lo, hi, zMin, az, Math.min(ax, bx));
                spanAt(lo, hi, zMin, az, Math.max(ax, bx));
                continue;
            }
            int z0 = Math.min(az, bz), z1 = Math.max(az, bz);
            for (int z = z0; z <= z1; z++) {
                int x = (int) Math.round(ax + (double) (bx - ax) * (z - az) / (bz - az));
                spanAt(lo, hi, zMin, z, x);
            }
        }
    }

    private static void spanAt(int[] lo, int[] hi, int zMin, int z, int x) {
        int zi = z - zMin;
        if (zi < 0 || zi >= lo.length) return;
        lo[zi] = Math.min(lo[zi], x);
        hi[zi] = Math.max(hi[zi], x);
    }

    /** True if (x,z) is inside the banded orthogonal region. */
    private static boolean inRegion(int[] lo, int[] hi, int zMin, int x, int z) {
        int zi = z - zMin;
        if (zi < 0 || zi >= lo.length || lo[zi] > hi[zi]) return false;
        return x >= lo[zi] && x <= hi[zi];
    }

    /** Pick one gate cell at the middle of each of the four sides, plus its along-segment neighbour (2 wide). */
    private static java.util.Set<Long> pickDoors(List<Col> cells, double[] centroid) {
        Col cn = null, cs = null, ce = null, cw = null;
        double bn = Double.MAX_VALUE, bs = Double.MAX_VALUE, be = Double.MAX_VALUE, bw = Double.MAX_VALUE;
        for (Col c : cells) {
            if (c.outward == null) continue; // corners aren't gates
            switch (c.outward) {
                case NORTH -> { double d = Math.abs(c.x - centroid[0]); if (d < bn) { bn = d; cn = c; } }
                case SOUTH -> { double d = Math.abs(c.x - centroid[0]); if (d < bs) { bs = d; cs = c; } }
                case EAST  -> { double d = Math.abs(c.z - centroid[1]); if (d < be) { be = d; ce = c; } }
                case WEST  -> { double d = Math.abs(c.z - centroid[1]); if (d < bw) { bw = d; cw = c; } }
                default -> { }
            }
        }
        java.util.Map<Long, Col> byPos = new java.util.HashMap<>();
        for (Col c : cells) byPos.put(pack(c.x, c.z), c);
        java.util.Set<Long> doors = new java.util.HashSet<>();
        markDoor(doors, byPos, cn);
        markDoor(doors, byPos, cs);
        markDoor(doors, byPos, ce);
        markDoor(doors, byPos, cw);
        return doors;
    }

    private static void markDoor(java.util.Set<Long> doors, java.util.Map<Long, Col> byPos, Col c) {
        if (c == null) return;
        doors.add(pack(c.x, c.z));
        int nx = c.x, nz = c.z;
        if (c.outward == Direction.NORTH || c.outward == Direction.SOUTH) nx++; else nz++;
        Col nb = byPos.get(pack(nx, nz));
        if (nb != null && nb.outward == c.outward) doors.add(pack(nx, nz)); // 2-wide if the neighbour matches
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** One wall column: 2D position and its outward cardinal (null = a corner, built as a 6-tall pillar). */
    private static final class Col {
        final int x, z;
        final Direction outward;
        Col(int x, int z, Direction outward) { this.x = x; this.z = z; this.outward = outward; }
    }
}
