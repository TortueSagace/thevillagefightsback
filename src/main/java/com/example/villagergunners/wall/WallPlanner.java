package com.example.villagergunners.wall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
    private static final int MAX_COLUMNS = 400;     // perimeter cap (runaway-village guard)
    private static final int MAX_BLOCKS = 3000;     // total placement cap

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

        // Rasterize the offset polygon into an ordered, 4-connected, de-duplicated set of columns.
        Map<Long, Col> columns = new LinkedHashMap<>();
        for (int i = 0; i < ring.size(); i++) {
            int[] a = ring.get(i);
            int[] b = ring.get((i + 1) % ring.size());
            Direction outward = outwardCardinal(a, b, centroid);
            boolean first = (i == 0);
            BiConsumer<Integer, Integer> sink = (x, z) -> {
                long key = pack(x, z);
                columns.putIfAbsent(key, new Col(x, z, outward));
            };
            line4(a[0], a[1], b[0], b[1], first, sink);
        }
        if (columns.size() > MAX_COLUMNS) return null;

        // Assign arc-length index s in perimeter order; expand each column into its course stack.
        List<WallBlock> blocks = new ArrayList<>();
        int s = 0;
        int minY = level.getMinBuildHeight();
        for (Col col : columns.values()) {
            col.s = s++;
            if (!level.hasChunk(col.x >> 4, col.z >> 4)) continue;     // never build into unloaded terrain
            int surfaceY = surfaceY(level, col.x, col.z, minY);
            if (surfaceY <= minY) continue;                            // void column -> skip
            expandColumn(col, surfaceY, blocks);
            if (blocks.size() > MAX_BLOCKS) return null;
        }
        if (blocks.isEmpty()) return null;

        blocks.sort(Comparator
                .comparingInt(WallBlock::course)
                .thenComparingInt(b -> b.pos().getX())
                .thenComparingInt(b -> b.pos().getZ())
                .thenComparingInt(b -> b.type().ordinal()));
        return new WallPlan(gravemind, blocks);
    }

    // --- column -> 5-course block stack ---

    private static void expandColumn(Col col, int surfaceY, List<WallBlock> out) {
        int base = surfaceY + 1;                 // wall sits ON the ground (one above the top solid block)
        Direction o = col.outward;
        boolean loophole = (col.s % 5 == 2);     // a firing slit every 5 blocks
        boolean ladder = (col.s % 15 == 0);      // a climb ladder every 15 blocks
        boolean merlon = (col.s % 2 == 0);       // battlements: alternating merlon / gap

        // Stairs form the loophole. FACING points toward the raised step; we face it INWARD so the open
        // notch (and thus the firing slit) opens OUTWARD. (Verify visually via the Phase-2 debug paste;
        // if the slit faces the wrong way, flip this single value to `o`.)
        Direction stairFacing = o.getOpposite();

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
            Direction in = o.getOpposite();
            BlockPos wall = new BlockPos(col.x, base, col.z);
            for (int c = 0; c <= 3; c++) {
                BlockPos lp = wall.relative(in).above(c);
                out.add(new WallBlock(lp, WallBlock.BlockType.LADDER, in, false, c));
            }
        }
    }

    private static WallBlock cobble(int x, int y, int z, int course) {
        return new WallBlock(new BlockPos(x, y, z), WallBlock.BlockType.COBBLE, null, false, course);
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
            if (isSolid(level, x, y, z)) {
                int yy = y - 1;
                int gap = 0;
                while (yy > minY && !isSolid(level, x, yy, z)) { yy--; gap++; }
                if (gap >= FLOAT_GAP && yy > minY) { y = yy; continue; } // floating island -> descend
                return y;                                                // real ground
            }
            y--;
        }
        return y;
    }

    private static boolean isSolid(LevelReader level, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(p);
        return s.isFaceSturdy(level, p, Direction.UP);
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

    /** Outward cardinal for edge a->b: the perpendicular that points away from the polygon centroid. */
    private static Direction outwardCardinal(int[] a, int[] b, double[] centroid) {
        double ex = b[0] - a[0], ez = b[1] - a[1];
        double nx = ez, nz = -ex;                       // one perpendicular
        double mx = (a[0] + b[0]) / 2.0, mz = (a[1] + b[1]) / 2.0;
        if ((mx - centroid[0]) * nx + (mz - centroid[1]) * nz < 0) { nx = -nx; nz = -nz; } // make it outward
        return Math.abs(nx) >= Math.abs(nz)
                ? (nx >= 0 ? Direction.EAST : Direction.WEST)
                : (nz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /** 4-connected integer line a->b (no diagonal-only steps, so the wall is gap-free). */
    private static void line4(int x0, int z0, int x1, int z1, boolean includeFirst, BiConsumer<Integer, Integer> out) {
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        int sx = Integer.signum(x1 - x0), sz = Integer.signum(z1 - z0);
        int x = x0, z = z0;
        if (includeFirst) out.accept(x, z);
        int err = dx - dz;
        while (x != x1 || z != z1) {
            int e2 = 2 * err;
            boolean stepX = e2 > -dz;
            boolean stepZ = e2 < dx;
            if (stepX && stepZ) {            // diagonal -> split into two orthogonal steps
                err -= dz; x += sx; out.accept(x, z);
                err += dx; z += sz; out.accept(x, z);
            } else if (stepX) {
                err -= dz; x += sx; out.accept(x, z);
            } else {
                err += dx; z += sz; out.accept(x, z);
            }
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** One wall column: 2D position, perimeter arc index, and the outward cardinal of its edge. */
    private static final class Col {
        final int x, z;
        final Direction outward;
        int s;
        Col(int x, int z, Direction outward) { this.x = x; this.z = z; this.outward = outward; }
    }
}
