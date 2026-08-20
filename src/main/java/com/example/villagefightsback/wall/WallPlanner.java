package com.example.villagefightsback.wall;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Pure, deterministic geometry for the village wall. Turns a set of bed positions into the full ordered
 * list of {@link WallBlock}s the BrainNode hands to the builders. Strategy:
 * beds -> distinct (x,z) -> a footprint grid marked within CLEARANCE of every bed AND of a minimum spanning
 * tree linking the beds (so the wall hugs the ACTUAL village shape -- an L stays an L instead of being
 * wrapped by a convex hull, which would enclose the L's notch as a big dark interior) -> the orthogonal
 * boundary of that region -> per-column ground height (skipping floating islands) -> expand each column into
 * its course stack (loophole stairs every 5, ladders every 15, battlements alternating). Everything is
 * integer/array based and sorted, so the same input always yields the identical plan.
 */
public final class WallPlanner {
    private WallPlanner() {}

    public static final int MIN_BEDS = 4;          // fewer distinct bed columns than this -> no wall yet
    private static final int CLEARANCE = 5;        // wall sits ~this many blocks out from the nearest bed
    private static final int FLOAT_GAP = 10;       // >= this much air below a surface => floating island, skip it
    private static final int MAX_GRID = 220 * 220; // footprint-grid cell cap (runaway-village guard)
    /**
     * The same guard for the stage-3 OUTER ring, which needs a far bigger one.
     *
     * <p>That survey scans {@code OUTER_SCAN_RADIUS} (140) blocks out, so its footprint spans up to 280 and
     * its grid up to 294x294 = 86,436 cells -- comfortably over MAX_GRID. The obsidian plan therefore came
     * back null EVERY time and no obsidian wall was ever built, for exactly the villages that had expanded
     * far enough to want one. The inner wall never hit it because it only scans 80.
     *
     * <p>Sized to what that scan can actually produce, with headroom. The cost is one boolean[] of about
     * 100 KB, allocated once per survey and thrown away, and the survey is throttled to every 200 ticks.
     */
    private static final int MAX_GRID_OUTER = 320 * 320;
    private static final int MAX_COLUMNS = 1000;    // perimeter cap
    private static final int MAX_BLOCKS = 6500;     // total placement cap
    private static final int DOOR_SPACING = 30;     // ~one gate (and so one builder + one defender) per this many perimeter columns
    private static final int MAX_DOORS = 12;        // ...capped at this many gates / builders / defenders
    private static final int MAX_WALL_ELEVATION = 10; // inner wall: ignore columns whose ground is >this far above/below the brain node
    private static final int CLUSTER_LINK = 24;    // two beds belong to the same village if within this far of each other

    /** Compute the inner (cobblestone) wall plan, or null if there aren't enough beds / the village is too large. */
    public static WallPlan plan(ServerLevel level, GlobalPos brainNode, List<BlockPos> beds) {
        return plan(level, brainNode, beds, false);
    }

    /** Compute the wall plan. {@code obsidian} = the stage-3 OUTER wall (obsidian + fake-obsidian gates). */
    public static WallPlan plan(ServerLevel level, GlobalPos brainNode, List<BlockPos> beds, boolean obsidian) {
        List<int[]> all = distinctXz(beds);
        List<int[]> pts = mainCluster(all, brainNode);

        // The village's OWN buildings are never outliers, whatever the clustering says.
        //
        // mainCluster keeps the component containing the brain node, linking points within CLUSTER_LINK (24).
        // That is right for a pillager outpost's beds and wrong for the stage-3 expansion: settlers found that
        // ring OUTSIDE the wall, the wall itself is not village material, and the gap from the ring back to the
        // core routinely exceeds 24 -- so the entire ring, grand houses and all, formed its own component and
        // was discarded. Sampling the buildings into the footprint did not help, because those samples landed
        // in the discarded component too.
        //
        // So anything standing inside a claimed building is added back unconditionally. StructureZones is the
        // register of what the village built, which is exactly the line between "ours, enclose it" and "someone
        // else's, ignore it" -- and it is the same test that decides a build may not carve through another.
        com.example.villagefightsback.infection.StructureZones zones =
                com.example.villagefightsback.infection.StructureZones.get(level);
        if (!zones.isEmpty()) {
            java.util.Set<Long> have = new java.util.HashSet<>();
            for (int[] q : pts) have.add((((long) q[0]) << 32) ^ (q[1] & 0xFFFFFFFFL));
            for (int[] q : all) {
                if (!have.add((((long) q[0]) << 32) ^ (q[1] & 0xFFFFFFFFL))) continue;
                // Column test: the sample carries no Y, so ask the index over the building's own height band.
                if (zones.intersectsForeign(q[0], level.getMinBuildHeight(), q[1],
                        q[0], level.getMaxBuildHeight() - 1, q[1])) {
                    pts.add(q);
                }
            }
        }
        if (pts.size() < MIN_BEDS) return null;

        // The inner wall hugs the village concavely, and on a big sprawling village that hug is what busts the
        // caps: a concave boundary that snakes between scattered houses is far longer than the same village's
        // convex outline, so MAX_COLUMNS/MAX_BLOCKS trip and the plan comes back null.
        //
        // Returning null there was a silent, PERMANENT failure. A village with no plan never gets a wall, never
        // gets wall defenders, and -- because InfectionManager.seedVillage lays its outward dirt-path ring along
        // the wall footprint -- never seeds its infection outward either. It could not even recover, since the
        // survey stops once the node reaches stage 2. Large pre-existing villages (a converted bell, 50+ claimed
        // buildings) hit this every time; a compact settler-founded village never did, which is why it hid.
        //
        // So a village too sprawling to hug is now walled by its convex hull instead. Same machinery the stage-3
        // obsidian ring already uses, one extra O(W*H) pass on a grid we have already built, and only on the
        // retry. A convex wall around a big village beats no wall at all.
        WallPlan p = build(level, brainNode, pts, obsidian, obsidian);
        if (p == null && !obsidian) p = build(level, brainNode, pts, false, true);
        return p;
    }

    private static WallPlan build(ServerLevel level, GlobalPos brainNode, List<int[]> pts,
                                  boolean obsidian, boolean convex) {
        double[] centroid = centroid(pts);

        // --- Concave footprint grid ---------------------------------------------------------------------
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : pts) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]);
        }
        int pad = CLEARANCE + 2;
        int ox = minX - pad, oz = minZ - pad;                 // grid origin in world coords
        int W = (maxX + pad) - ox + 1, H = (maxZ + pad) - oz + 1;
        if ((long) W * H > (obsidian ? MAX_GRID_OUTER : MAX_GRID)) return null; // footprint too sprawling

        boolean[] interior = new boolean[W * H];
        for (int[] p : pts) markDisc(interior, W, H, p[0] - ox, p[1] - oz);          // CLEARANCE around each bed
        // CONVEX (the stage-3 obsidian ring) skips all of this. The MST, the tubes it rasterises and the
        // pocket fill exist to join scattered houses into ONE concave ring -- and convexify below subsumes
        // every one of them: the hull of the disc-marked points is identical whether or not they were
        // connected first.
        //
        // Skipping it is not a micro-optimisation. spanningEdges is Prim's algorithm, O(n^2), and the outer
        // survey feeds it every village-material column within OUTER_SCAN_RADIUS (140) -- thousands of points,
        // so tens of millions of distance computations in a single tick. That is the multi-second freeze on
        // reaching stage 3, and for the convex case it bought nothing at all.
        if (!convex) {
            for (int[] e : spanningEdges(pts)) {                                          // ...and along the MST,
                int[] a = pts.get(e[0]), b = pts.get(e[1]);                               // so scattered houses
                rasterizeTube(interior, W, H, a[0] - ox, a[1] - oz, b[0] - ox, b[1] - oz); // enclose as ONE ring
            }

            // Close any pocket of non-interior cells the footprint completely surrounds. The boundary pass below
            // calls ANY interior cell with an outside neighbour a wall column, and an enclosed courtyard is exactly
            // that -- so without this a gap in the middle of a village grows its own little wall ring inside the
            // real one. (The obsidian wall never showed this: convexify fills every pocket on its way past.)
            fillEnclosed(interior, W, H);
        }

        // The OUTER (obsidian) wall is deliberately CONVEX -- the opposite of the inner wall's concave hug.
        // Replace the footprint with its filled convex hull (which still clears every bed, since the hull is
        // taken of the already-CLEARANCE-expanded cells). The inner wall also lands here on its too-sprawling
        // retry (see plan).

        if (convex) convexify(interior, W, H);

        // --- Boundary cells: an interior cell with a non-interior orthogonal neighbour. outward = the
        // outside side (a cell with >=2 outside sides is a corner -> 6-tall pillar). This naturally follows
        // concavities and inserts pillars at diagonal steps, so the wall is 4-connected (sealed).
        List<Col> cells = new ArrayList<>();
        for (int gz = 0; gz < H; gz++) {
            for (int gx = 0; gx < W; gx++) {
                if (!interior[gz * W + gx]) continue;
                boolean wN = !cell(interior, W, H, gx, gz - 1);
                boolean wS = !cell(interior, W, H, gx, gz + 1);
                boolean wE = !cell(interior, W, H, gx + 1, gz);
                boolean wW = !cell(interior, W, H, gx - 1, gz);
                int cnt = (wN ? 1 : 0) + (wS ? 1 : 0) + (wE ? 1 : 0) + (wW ? 1 : 0);
                if (cnt == 0) continue; // interior cell
                Direction o = cnt >= 2 ? null // corner -> pillar
                        : wN ? Direction.NORTH : wS ? Direction.SOUTH : wE ? Direction.EAST : Direction.WEST;
                cells.add(new Col(ox + gx, oz + gz, o));
            }
        }
        if (cells.isEmpty() || cells.size() > MAX_COLUMNS) return null;

        // Order the columns AROUND the village centre (by angle) so the per-layer builders all sweep the
        // perimeter the same way from the same start point.
        cells.sort(Comparator.comparingDouble(c -> Math.atan2(c.z - centroid[1], c.x - centroid[0])));

        // Gates: one 2-wide fake-cobblestone passage at the middle of each of the four sides.
        // Number of gates (== builders == defenders) scales with the wall's size, capped at MAX_DOORS, and
        // the gates are spread evenly around the ring so each builder gets a near-equal-length section.
        int doorCount = Math.max(1, Math.min(MAX_DOORS, Math.round(cells.size() / (float) DOOR_SPACING)));
        java.util.Set<Long> doors = pickDoors(cells, doorCount);

        int minY = level.getMinBuildHeight();
        int n = cells.size();
        int[] surf = new int[n];     // top of the column the wall's courses sit on (the WATER surface, over water)
        int[] foot = new int[n];     // what the column actually rests on: the ground, or the bed under the water
        boolean[] skip = new boolean[n];
        for (int i = 0; i < n; i++) {
            Col col = cells.get(i);
            if (!level.hasChunk(col.x >> 4, col.z >> 4)) { skip[i] = true; continue; } // unloaded terrain
            int sy = surfaceY(level, col.x, col.z, minY);
            surf[i] = sy;
            foot[i] = sy;
            if (sy <= minY) { skip[i] = true; continue; }              // void column -> skip
            // What the column would rest on: an OPEN fluid surface (not a waterlogged solid, which is real ground).
            BlockState ground = level.getBlockState(new BlockPos(col.x, sy, col.z));
            if (!ground.blocksMotion()) {
                if (ground.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) { skip[i] = true; continue; }
                if (ground.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                    // A wall DOES cross water, and it is sealed down to the bed -- a wall a mob can swim under
                    // is not a wall. The bed, not the waterline, is what the column stands on.
                    int bed = solidUnder(level, col.x, sy, col.z, minY);
                    if (bed <= minY) { skip[i] = true; continue; }     // bottomless -> nothing to build on
                    foot[i] = bed;
                }
            }
            // Inner wall: skip columns whose footing sits more than MAX_WALL_ELEVATION blocks above or below the
            // brain node, so builders don't trek into a deep pit (or up a cliff) to wall a section far off the
            // village's level. The stage-3 OUTER (obsidian) wall is exempt -- it rings the spread-out expansion.
            //
            // Measured on the FOOTING, which is what makes deep water take care of itself: a river bed is within
            // reach and gets walled, while an ocean floor is a deep pit by this very rule and is skipped, with no
            // separate "how far may a wall bridge water" limit needed.
            if (!obsidian && Math.abs(foot[i] - brainNode.pos().getY()) > MAX_WALL_ELEVATION) {
                skip[i] = true;
            }

        }

        List<List<WallBlock>> columns = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < n; i++) {
            if (skip[i]) continue;
            Col col = cells.get(i);
            List<WallBlock> colBlocks = new ArrayList<>();
            expandColumn(col, surf[i], foot[i] + 1, colBlocks, doors.contains(pack(col.x, col.z)), obsidian);
            if (!colBlocks.isEmpty()) { columns.add(colBlocks); total += colBlocks.size(); }
            if (total > MAX_BLOCKS) return null;
        }
        if (columns.isEmpty()) return null;
        return new WallPlan(brainNode, columns);
    }


    /**
     * Keep only the bed cluster the brain node belongs to, and drop every outlier.
     *
     * <p>Anything with a bed inside the village radius used to be claimed as part of the village, so a
     * pillager outpost 80 blocks out dragged the whole ring out with it: the inner wall stretched far past
     * the village, stage-3 buildings were then placed against that inflated footprint, and the obsidian wall
     * gave up entirely.
     *
     * <p>A village is a dense cluster -- no house sits more than about ten blocks from its neighbour -- so
     * membership is single-linkage: beds within {@value #CLUSTER_LINK} of each other are the same village, and
     * the component containing the brain node is the village. {@value #CLUSTER_LINK} is generous against a
     * legitimately spread-out village while an outlier at 80 blocks is nowhere near joining.
     *
     * <p>Cheap by bucketing: beds go into cells {@value #CLUSTER_LINK} across, so a bed can only link to one of
     * the nine cells around it and never compares against the whole village. That is O(n) with a small
     * constant instead of the O(n^2) every pair would cost, and it runs once per plan, not per tick.
     */
    private static List<int[]> mainCluster(List<int[]> pts, GlobalPos brainNode) {
        int n = pts.size();
        if (n <= 1) return pts;

        Map<Long, List<Integer>> buckets = new HashMap<>();
        for (int i = 0; i < n; i++) {
            buckets.computeIfAbsent(bucketKey(pts.get(i)[0], pts.get(i)[1]), k -> new ArrayList<>()).add(i);
        }

        int seed = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            long dx = pts.get(i)[0] - brainNode.pos().getX();
            long dz = pts.get(i)[1] - brainNode.pos().getZ();
            long d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; seed = i; }
        }

        long link2 = (long) CLUSTER_LINK * CLUSTER_LINK;
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen[seed] = true;
        queue.add(seed);
        List<int[]> out = new ArrayList<>();
        while (!queue.isEmpty()) {
            int[] a = pts.get(queue.poll());
            out.add(a);
            long cx = Math.floorDiv(a[0], CLUSTER_LINK);
            long cz = Math.floorDiv(a[1], CLUSTER_LINK);
            for (long gx = cx - 1; gx <= cx + 1; gx++) {
                for (long gz = cz - 1; gz <= cz + 1; gz++) {
                    List<Integer> bucket = buckets.get((gx << 32) ^ (gz & 0xFFFFFFFFL));
                    if (bucket == null) continue;
                    for (int j : bucket) {
                        if (seen[j]) continue;
                        int[] b = pts.get(j);
                        long dx = a[0] - b[0], dz = a[1] - b[1];
                        if (dx * dx + dz * dz <= link2) { seen[j] = true; queue.add(j); }
                    }
                }
            }
        }
        return out;
    }

    private static long bucketKey(int x, int z) {
        long gx = Math.floorDiv(x, CLUSTER_LINK);
        long gz = Math.floorDiv(z, CLUSTER_LINK);
        return (gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    /**
     * Mark every non-interior cell the footprint fully encloses as interior, so only the OUTER edge survives
     * the boundary pass. Flood the outside inward from the grid border: whatever the flood cannot reach is a
     * pocket. The border is always outside, because the grid is padded by CLEARANCE + 2 around every bed.
     */
    private static void fillEnclosed(boolean[] interior, int W, int H) {
        boolean[] outside = new boolean[W * H];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int gx = 0; gx < W; gx++) {
            flood(interior, outside, queue, W, H, gx, 0);
            flood(interior, outside, queue, W, H, gx, H - 1);
        }
        for (int gz = 0; gz < H; gz++) {
            flood(interior, outside, queue, W, H, 0, gz);
            flood(interior, outside, queue, W, H, W - 1, gz);
        }
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int gx = idx % W, gz = idx / W;
            flood(interior, outside, queue, W, H, gx + 1, gz);
            flood(interior, outside, queue, W, H, gx - 1, gz);
            flood(interior, outside, queue, W, H, gx, gz + 1);
            flood(interior, outside, queue, W, H, gx, gz - 1);
        }
        for (int i = 0; i < interior.length; i++) {
            if (!interior[i] && !outside[i]) interior[i] = true;   // an enclosed pocket -> part of the village
        }
    }

    private static void flood(boolean[] interior, boolean[] outside, ArrayDeque<Integer> queue,
                              int W, int H, int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= W || gz >= H) return;
        int idx = gz * W + gx;
        if (interior[idx] || outside[idx]) return;
        outside[idx] = true;
        queue.add(idx);
    }

    // --- column -> course stack ---

    private static void expandColumn(Col col, int surfaceY, int plugFromY, List<WallBlock> out,
                                     boolean isDoor, boolean obsidian) {
        int base = surfaceY + 1;                 // wall sits ON the ground (one above the top solid block)

        // Over water, plug the submerged part first: solid from the bed up to and including the waterline, so
        // the courses above sit on masonry rather than floating over a swimmable gap. On dry land plugFromY is
        // surfaceY + 1 and this loop does nothing. Course 0 so builders lay the footing before the curtain.
        for (int y = plugFromY; y <= surfaceY; y++) out.add(solid(col.x, y, col.z, 0, obsidian));

        // Corners (where two straight segments meet): a plain 4-tall solid pillar, nothing else.
        if (col.outward == null) {
            for (int c = 0; c <= 3; c++) out.add(solid(col.x, base + c, col.z, c, obsidian));
            return;
        }

        Direction o = col.outward;
        Direction in = o.getOpposite();          // toward the village interior

        if (isDoor) {
            // A 3-high fake-gate (courses 0..2) with a solid cap above (course 3) -- the opening stays a clean
            // 2-wide x 3-high passage for an iron golem. The only thing ON the column is a standing torch atop
            // the cap (the 4th block above the door), to light the gate. No loophole/ladder.
            out.add(fakeGate(col.x, base, col.z, 0, obsidian));
            out.add(fakeGate(col.x, base + 1, col.z, 1, obsidian));
            out.add(fakeGate(col.x, base + 2, col.z, 2, obsidian));
            out.add(solid(col.x, base + 3, col.z, 3, obsidian));
            // Standing torch on top of the cap (course 3 so it's placed last, after the cap is up).
            out.add(new WallBlock(new BlockPos(col.x, base + 4, col.z),
                    WallBlock.BlockType.STANDING_TORCH, null, false, 3));
            return;
        }

        // Spacing runs ALONG the straight segment: vertical segments (outward E/W) along Z, horizontal (N/S)
        // along X. floorMod keeps it correct for negative coordinates.
        int coord = (o == Direction.EAST || o == Direction.WEST) ? col.z : col.x;
        boolean loophole = Math.floorMod(coord, 5) == 2;   // a firing slit every 5 blocks
        boolean ladder = Math.floorMod(coord, 15) == 0;    // a climb ladder every 15 blocks
        boolean torch = Math.floorMod(coord, 6) == 3 && !ladder; // light the inside face (not on ladder columns)

        // Stairs form the loophole. Their FACING must run ALONG the wall (parallel), not across it -- only
        // then does the L/Gamma notch leave an actual hole THROUGH the 1-thick wall to shoot from. (If the
        // slit ends up on the wrong side, flip this to o.getCounterClockWise().)
        Direction stairFacing = o.getClockWise();

        out.add(solid(col.x, base, col.z, 0, obsidian));
        out.add(loophole ? stair(col.x, base + 1, col.z, 1, stairFacing, false, obsidian)
                         : solid(col.x, base + 1, col.z, 1, obsidian));
        out.add(loophole ? stair(col.x, base + 2, col.z, 2, stairFacing, true, obsidian)
                         : solid(col.x, base + 2, col.z, 2, obsidian));
        out.add(solid(col.x, base + 3, col.z, 3, obsidian));

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

    /** A solid wall block: obsidian for the stage-3 outer wall, cobblestone otherwise. */
    private static WallBlock solid(int x, int y, int z, int course, boolean obsidian) {
        return new WallBlock(new BlockPos(x, y, z),
                obsidian ? WallBlock.BlockType.OBSIDIAN : WallBlock.BlockType.COBBLE, null, false, course);
    }

    /** A gate (ally-phasing) block: fake obsidian for the outer wall, fake cobblestone otherwise. */
    private static WallBlock fakeGate(int x, int y, int z, int course, boolean obsidian) {
        return new WallBlock(new BlockPos(x, y, z),
                obsidian ? WallBlock.BlockType.FAKE_OBSIDIAN : WallBlock.BlockType.FAKE_COBBLE, null, false, course);
    }

    /** A loophole stair: obsidian stairs on the stage-3 outer wall, cobblestone stairs otherwise. */
    private static WallBlock stair(int x, int y, int z, int course, Direction facing, boolean topHalf, boolean obsidian) {
        return new WallBlock(new BlockPos(x, y, z),
                obsidian ? WallBlock.BlockType.OBSIDIAN_STAIR : WallBlock.BlockType.STAIR, facing, topHalf, course);
    }

    // --- terrain height with the floating-island rule ---

    /**
     * The Y of the solid block the wall column rests on. Starts at the surface heightmap and, if that
     * surface has a big air gap ({@value #FLOAT_GAP}+) below it before the next solid (a floating island),
     * descends to the real ground. A normal surface with a cave under it has solid rock just below, so it
     * is kept (we do not dig into caves).
     */
    /**
     * The highest block that actually blocks motion strictly below {@code fromY}: a water column's bed.
     *
     * <p>{@code surfaceY} deliberately treats a fluid as a surface, so over water it stops at the waterline.
     * This walks on down through the water to what the wall must really stand on. Returns {@code minY} if the
     * column is bottomless, which the caller reads as "skip".
     */
    private static int solidUnder(LevelReader level, int x, int fromY, int z, int minY) {
        for (int y = fromY - 1; y > minY; y--) {
            if (level.getBlockState(new BlockPos(x, y, z)).blocksMotion()) return y;
        }
        return minY;
    }

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
        // Trees + foliage are NOT ground: a wall column ignores logs, leaves and fertile grass -- vanilla AND the
        // counter-infection's fertile/rich variants -- and sits on the real ground below, then rises straight
        // through the trunk/canopy/grass (builders replace those blocks). Without these checks a wall could land
        // on a fertile tree's canopy (or treat fertile grass as ground), leaving a hole beneath it.
        if (s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES)
                || isFertileFoliage(s)) return false;
        return s.blocksMotion() || !s.getFluidState().isEmpty();
    }

    /** True for our fertile/rich LOG or LEAVES blocks, and Fertile Grass -- none of which are real ground a wall
     *  should rest on (and none are in the vanilla #logs/#leaves tags). */
    private static boolean isFertileFoliage(BlockState s) {
        net.minecraft.resources.ResourceLocation id =
                com.example.villagefightsback.platform.ModRegistries.BLOCKS.getKey(s.getBlock());
        if (id == null || !id.getNamespace().equals(com.example.villagefightsback.TheVillageFightsBack.MODID)) {
            return false;
        }
        String p = id.getPath();
        return p.endsWith("_log") || p.endsWith("_leaves") || p.equals("fertile_grass");
    }

    // --- 2D footprint helpers ---

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

    /** Mark every grid cell within CLEARANCE (Euclidean) of (cx,cz) as interior -- a disc of radius CLEARANCE. */
    private static void markDisc(boolean[] grid, int W, int H, int cx, int cz) {
        for (int dz = -CLEARANCE; dz <= CLEARANCE; dz++) {
            int z = cz + dz;
            if (z < 0 || z >= H) continue;
            for (int dx = -CLEARANCE; dx <= CLEARANCE; dx++) {
                int x = cx + dx;
                if (x < 0 || x >= W) continue;
                if (dx * dx + dz * dz <= CLEARANCE * CLEARANCE) grid[z * W + x] = true;
            }
        }
    }

    /** Mark a CLEARANCE-radius tube (capsule) along the segment (x0,z0)->(x1,z1): connects two bed clusters. */
    private static void rasterizeTube(boolean[] grid, int W, int H, int x0, int z0, int x1, int z1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? x0 : Math.round(x0 + (float) (x1 - x0) * i / steps);
            int z = steps == 0 ? z0 : Math.round(z0 + (float) (z1 - z0) * i / steps);
            markDisc(grid, W, H, x, z);
        }
    }

    /** Overwrite {@code grid} with the FILLED CONVEX HULL of its currently-marked cells -- turning the concave
     *  footprint into a convex one for the outer wall. No-op (leaves the grid as-is) if the marked cells are
     *  degenerate (fewer than 3, or all collinear). */
    private static void convexify(boolean[] grid, int W, int H) {
        List<int[]> pts = new ArrayList<>();
        for (int gz = 0; gz < H; gz++)
            for (int gx = 0; gx < W; gx++)
                if (grid[gz * W + gx]) pts.add(new int[]{gx, gz});
        if (pts.size() < 3) return;
        int[][] hull = convexHull(pts);
        if (hull.length < 3) return;                  // all marked cells collinear -> leave the footprint be
        for (int gz = 0; gz < H; gz++)
            for (int gx = 0; gx < W; gx++)
                grid[gz * W + gx] = inConvex(hull, gx, gz);
    }

    /** Convex hull (CCW, no repeated endpoint) of a set of grid cells -- Andrew's monotone chain. */
    private static int[][] convexHull(List<int[]> pts) {
        int n = pts.size();
        List<int[]> p = new ArrayList<>(pts);
        p.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int[][] hull = new int[2 * n][];
        int k = 0;
        for (int i = 0; i < n; i++) {                 // lower hull
            int[] pi = p.get(i);
            while (k >= 2 && cross(hull[k - 2], hull[k - 1], pi) <= 0) k--;
            hull[k++] = pi;
        }
        int lower = k + 1;
        for (int i = n - 2; i >= 0; i--) {            // upper hull
            int[] pi = p.get(i);
            while (k >= lower && cross(hull[k - 2], hull[k - 1], pi) <= 0) k--;
            hull[k++] = pi;
        }
        return java.util.Arrays.copyOf(hull, k - 1);  // drop the duplicated start point
    }

    /** (a-o) x (b-o): &gt;0 left turn (CCW), &lt;0 right turn, 0 collinear. */
    private static long cross(int[] o, int[] a, int[] b) {
        return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** True if cell (x,z) is inside or on the CCW convex polygon {@code hull}. */
    private static boolean inConvex(int[][] hull, int x, int z) {
        int[] p = {x, z};
        for (int i = 0, m = hull.length; i < m; i++) {
            if (cross(hull[i], hull[(i + 1) % m], p) < 0) return false; // strictly outside one edge
        }
        return true;
    }

    /** Minimum spanning tree over the beds (Prim, O(n^2)); returns index pairs. Guarantees one connected ring. */
    private static List<int[]> spanningEdges(List<int[]> pts) {
        int n = pts.size();
        List<int[]> edges = new ArrayList<>();
        if (n < 2) return edges;
        boolean[] in = new boolean[n];
        long[] best = new long[n];
        int[] from = new int[n];
        java.util.Arrays.fill(best, Long.MAX_VALUE);
        in[0] = true;
        for (int j = 1; j < n; j++) { best[j] = distSq(pts.get(0), pts.get(j)); from[j] = 0; }
        for (int k = 1; k < n; k++) {
            int u = -1;
            long bd = Long.MAX_VALUE;
            for (int j = 0; j < n; j++) if (!in[j] && best[j] < bd) { bd = best[j]; u = j; }
            if (u < 0) break;
            in[u] = true;
            edges.add(new int[]{from[u], u});
            for (int j = 0; j < n; j++) if (!in[j]) {
                long d = distSq(pts.get(u), pts.get(j));
                if (d < best[j]) { best[j] = d; from[j] = u; }
            }
        }
        return edges;
    }

    private static long distSq(int[] a, int[] b) {
        long dx = a[0] - b[0], dz = a[1] - b[1];
        return dx * dx + dz * dz;
    }

    private static boolean cell(boolean[] grid, int W, int H, int gx, int gz) {
        if (gx < 0 || gx >= W || gz < 0 || gz >= H) return false;
        return grid[gz * W + gx];
    }

    /** Pick one gate cell at the middle of each of the four sides, plus its along-segment neighbour (2 wide). */
    /**
     * Place {@code doorCount} gates at evenly-spaced positions around the (angle-ordered) perimeter, so each
     * builder's door-to-door section spans a near-equal number of columns. Each gate is the first flat
     * (non-corner) column at/after its even-spacing index, marked 2-wide.
     */
    private static java.util.Set<Long> pickDoors(List<Col> cells, int doorCount) {
        java.util.Set<Long> doors = new java.util.HashSet<>();
        int n = cells.size();
        if (n == 0 || doorCount <= 0) return doors;
        java.util.Map<Long, Col> byPos = new java.util.HashMap<>();
        for (Col c : cells) byPos.put(pack(c.x, c.z), c);
        for (int j = 0; j < doorCount; j++) {
            int target = (int) ((long) j * n / doorCount);
            for (int k = 0; k < n; k++) {                      // first flat column at/after the spacing index
                Col c = cells.get((target + k) % n);
                if (c.outward == null) continue;               // corners aren't gates
                if (doors.contains(pack(c.x, c.z))) continue;  // don't double up an existing gate
                markDoor(doors, byPos, c);
                break;
            }
        }
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
