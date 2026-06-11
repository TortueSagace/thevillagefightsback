package com.example.villagergunners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.villagergunners.wall.WallBlock;
import com.example.villagergunners.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds the village's protective reinforced-glass dome (gravemind level 4). Geometry:
 *  1. Find the highest wall tip H, then raise every wall column with a vertical glass pillar up to H, so the
 *     whole rim sits on one horizontal plane.
 *  2. Flood-fill the (x,z) interior enclosed by the wall (gates block the fill, so it can't leak out).
 *  3. Cap the interior with a gentle paraboloid roof: y = H + rise*(1 - (d/R)^2), peaking over the gravemind
 *     (d=0) and meeting the rim at H (d=R). Each column is filled DOWN to just above its lowest neighbour, so
 *     vertical steps are walled off -> a watertight (hole-free) shell that stops aerial attacks.
 * The whole thing is handed to {@link HouseBuildManager} so it rises block-by-block like the TNT structures.
 */
public final class DomeBuilder {
    private DomeBuilder() {}

    private static final int MAX_INTERIOR = 14_000; // safety cap (huge villages); flood stops here
    private static final double RISE_FACTOR = 0.22; // dome peak height as a fraction of its radius
    private static final int RISE_MIN = 2, RISE_MAX = 10;
    private static final int CLEARANCE = 3;         // air blocks the dome keeps above a building's roof
    private static final int MAX_DOME_HEIGHT = 64;  // cap above the rim so a freak-tall build can't spike forever

    private static long key(int x, int z) { return (x & 0xFFFFFFFFL) | ((long) z << 32); }
    private static int kx(long k) { return (int) k; }
    private static int kz(long k) { return (int) (k >> 32); }

    /** Compute the dome and submit it to the block-by-block builder. No-op if there's no usable wall. */
    public static void build(ServerLevel level, WallPlan plan, BlockPos gravemind) {
        BlockState glass = ModBlocks.REINFORCED_GLASS.get().defaultBlockState();

        // 1. Wall column tops + bounding box + highest tip H.
        Map<Long, Integer> wallTop = new HashMap<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, H = Integer.MIN_VALUE;
        for (WallBlock wb : plan.blocks()) {
            BlockPos p = wb.pos();
            long k = key(p.getX(), p.getZ());
            Integer cur = wallTop.get(k);
            if (cur == null || p.getY() > cur) wallTop.put(k, p.getY());
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        if (wallTop.isEmpty()) return;
        for (int v : wallTop.values()) H = Math.max(H, v);

        List<HouseBuildManager.Placement> order = new ArrayList<>();

        // 2. Raise each wall column to the common top plane H (vertical glass).
        for (Map.Entry<Long, Integer> e : wallTop.entrySet()) {
            int x = kx(e.getKey()), z = kz(e.getKey());
            for (int y = e.getValue() + 1; y <= H; y++) order.add(place(x, y, z, glass));
        }

        // 3. Flood-fill the interior (gates are wall columns, so they seal the fill).
        int cx = gravemind.getX(), cz = gravemind.getZ();
        Set<Long> interior = floodInterior(cx, cz, wallTop.keySet(), minX, maxX, minZ, maxZ);

        if (!interior.isEmpty()) {
            double r = 1.0;
            for (long k : wallTop.keySet()) r = Math.max(r, Math.hypot(kx(k) - cx, kz(k) - cz));
            final double R = r;
            int rise = Math.max(RISE_MIN, Math.min(RISE_MAX, (int) Math.round(R * RISE_FACTOR)));

            // Dome height per interior column: the gentle paraboloid cap, but DRAPED so it always clears the
            // tallest block in that column by CLEARANCE (so a central skyscraper / any building is enclosed
            // rather than poking through). Capped so a freak-tall pillar can't spike the dome forever.
            Map<Long, Integer> top = new HashMap<>(interior.size() * 2);
            for (long k : interior) {
                int x = kx(k), z = kz(k);
                double d = Math.hypot(x - cx, z - cz);
                double f = Math.max(0.0, 1.0 - (d * d) / (R * R));
                int paraboloid = H + (int) Math.round(rise * f);
                int clearBuildings = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + CLEARANCE;
                int y = Math.max(paraboloid, clearBuildings);
                if (y > H + MAX_DOME_HEIGHT) y = H + MAX_DOME_HEIGHT;
                top.put(k, y);
            }
            // Watertight fill: each column drops to its LOWEST orthogonal neighbour's top, so adjacent columns
            // always share a layer -> no holes (even at diagonal steps), a continuous shell.
            for (long k : interior) {
                int x = kx(k), z = kz(k), yTop = top.get(k);
                int floorY = yTop;
                Integer n;
                if ((n = top.get(key(x + 1, z))) != null) floorY = Math.min(floorY, n);
                if ((n = top.get(key(x - 1, z))) != null) floorY = Math.min(floorY, n);
                if ((n = top.get(key(x, z + 1))) != null) floorY = Math.min(floorY, n);
                if ((n = top.get(key(x, z - 1))) != null) floorY = Math.min(floorY, n);
                if (floorY < H) floorY = H;                       // never below the rim plane
                for (int y = floorY; y <= yTop; y++) order.add(place(x, y, z, glass));
            }
        }

        // Keep the level-2 gate defenders working: carve the dome out of each defender shell (its 3x3
        // protecting-glass box + a block of clearance above), so the wall-raise/roof never entombs the turret
        // and it can still fire outward over the wall. The shell itself (solid glass) seals that spot.
        List<BlockPos> gates = plan.gates();
        if (!gates.isEmpty()) {
            int sc = WallPlan.COURSES; // shell-centre offset above the gate (matches buildDefenderShell)
            order.removeIf(p -> {
                BlockPos q = p.pos();
                for (BlockPos g : gates) {
                    if (Math.abs(q.getX() - g.getX()) <= 1 && Math.abs(q.getZ() - g.getZ()) <= 1
                            && q.getY() >= g.getY() + sc - 1 && q.getY() <= g.getY() + sc + 2) return true;
                }
                return false;
            });
        }

        if (order.isEmpty()) return;
        order.sort(Comparator.comparingInt(p -> p.pos().getY())); // rise bottom-up, like the TNT builds
        HouseBuildManager.submit(level, order, null, List.of());
    }

    private static HouseBuildManager.Placement place(int x, int y, int z, BlockState glass) {
        return new HouseBuildManager.Placement(new BlockPos(x, y, z), glass, null, false);
    }

    /** 4-connected (x,z) flood from the gravemind, blocked by wall columns and the wall's bounding box. */
    private static Set<Long> floodInterior(int cx, int cz, Set<Long> wall, int minX, int maxX, int minZ, int maxZ) {
        Set<Long> seen = new HashSet<>();
        long start = key(cx, cz);
        if (wall.contains(start)) return seen; // gravemind sits on the wall (shouldn't happen) -> bail
        ArrayDeque<Long> q = new ArrayDeque<>();
        q.add(start); seen.add(start);
        while (!q.isEmpty() && seen.size() < MAX_INTERIOR) {
            long c = q.poll();
            int x = kx(c), z = kz(c);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue; // bounded by wall extent
                long nk = key(nx, nz);
                if (wall.contains(nk) || seen.contains(nk)) continue;
                seen.add(nk);
                q.add(nk);
            }
        }
        return seen;
    }
}
