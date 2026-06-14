package com.example.villagefightsback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * One-shot "furnish the village" pass run when a Village Brain Node spawns. It finds village house
 * interiors near the brain node and drops up to two beds in each bedless one:
 *   1. Scan the radius for INTERIOR FLOOR cells: air to stand in, a solid floor below, and a roof
 *      above (so it's indoors, not open sky).
 *   2. Flood-fill connected interior cells into rooms, then keep only rooms whose footprint is a
 *      mostly-filled rectangle of house size (the strongest "this is a house" signal) AND whose walls
 *      are building blocks (logs / planks / cobblestone family / sandstone / stone bricks).
 *   3. In each bedless room, place up to two beds on the floor, keeping doors (and the path through
 *      them) clear.
 *
 * Conservative: it never destroys solid blocks, only fills empty interior space.
 */
public final class HouseBedFiller {
    private HouseBedFiller() {}

    private static final int RADIUS = 32;     // horizontal scan radius around the brain node
    private static final int Y_BAND = 8;      // vertical scan band (+/-) around the brain node
    private static final int ROOF_MAX = 6;    // a roof must be within this many blocks above
    private static final int MIN_ROOM = 4;    // ignore tiny gaps
    private static final int MAX_ROOM = 400;  // ignore huge open spaces (caves, etc.)
    private static final int MIN_DIM = 2;     // house footprint dimensions
    private static final int MAX_DIM = 16;
    private static final double RECT_FILL = 0.70; // footprint cells / bbox area: how "rectangular" it must be
    private static final int MIN_WALL_BLOCKS = 4;  // building-block wall cells required to count as built
    private static final int BEDS_PER_ROOM = 2;
    private static final int MAX_BEDS = 60;   // total safety cap per village

    /** Furnish houses near {@code center}; returns the number of beds placed. Server-side. */
    public static int fill(ServerLevel level, BlockPos center) {
        Set<BlockPos> interior = new HashSet<>();
        scan(level, center, interior);

        int placed = 0;
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos start : interior) {
            if (placed >= MAX_BEDS) break;
            if (visited.contains(start)) continue;
            List<BlockPos> room = floodFill(start, interior, visited);
            if (room.size() < MIN_ROOM || room.size() > MAX_ROOM) continue;
            if (!isRectangularHouse(room)) continue;     // strongest signal: rectangular footprint
            Set<BlockPos> roomSet = new HashSet<>(room);
            if (!wallsAreBuilt(level, room, roomSet)) continue;
            if (roomHasBed(level, room)) continue;        // already furnished
            placed += placeBeds(level, room, roomSet, doorwayCells(level, room, roomSet));
        }
        return placed;
    }

    /** Collect interior-floor cells (air with headroom, solid floor, roofed) in the scan box. */
    private static void scan(ServerLevel level, BlockPos center, Set<BlockPos> interior) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -Y_BAND; dy <= Y_BAND; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).isAir() && isInteriorFloor(level, p)) {
                        interior.add(p.immutable());
                    }
                }
            }
        }
    }

    private static boolean isInteriorFloor(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p.above()).isAir()) return false;                  // headroom
        BlockPos below = p.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false; // floor
        for (int k = 2; k <= ROOF_MAX; k++) {
            if (!level.getBlockState(p.above(k)).isAir()) return true;              // roofed -> indoors
        }
        return false;                                                              // open sky -> outdoors
    }

    private static List<BlockPos> floodFill(BlockPos start, Set<BlockPos> interior, Set<BlockPos> visited) {
        List<BlockPos> room = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            room.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (interior.contains(n) && visited.add(n)) queue.add(n);
            }
        }
        return room;
    }

    /** A house: the 2D footprint is a mostly-filled rectangle of house-sized dimensions. */
    private static boolean isRectangularHouse(List<BlockPos> room) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        Set<Long> footprint = new HashSet<>();
        for (BlockPos p : room) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            footprint.add(((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL));
        }
        int w = maxX - minX + 1, d = maxZ - minZ + 1;
        if (w < MIN_DIM || d < MIN_DIM || w > MAX_DIM || d > MAX_DIM) return false;
        return (double) footprint.size() / (w * d) >= RECT_FILL;
    }

    /** Confirm it's a built structure: enough of the surrounding wall cells are building blocks. */
    private static boolean wallsAreBuilt(ServerLevel level, List<BlockPos> room, Set<BlockPos> roomSet) {
        int built = 0;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos c : room) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = c.relative(dir);
                if (roomSet.contains(n) || !seen.add(n)) continue;
                if (isBuildingBlock(level.getBlockState(n)) && ++built >= MIN_WALL_BLOCKS) return true;
            }
        }
        return false;
    }

    private static boolean isBuildingBlock(BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return true;
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.STONE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.CUT_SANDSTONE)
                || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.CHISELED_SANDSTONE);
    }

    private static boolean roomHasBed(ServerLevel level, List<BlockPos> room) {
        for (BlockPos c : room) {
            if (level.getBlockState(c).getBlock() instanceof BedBlock) return true;
        }
        return false;
    }

    /** Cells in/around doorways that must stay clear: the opening cell plus the cell just inside it. */
    private static Set<BlockPos> doorwayCells(ServerLevel level, List<BlockPos> room, Set<BlockPos> roomSet) {
        Set<BlockPos> blocked = new HashSet<>();
        for (BlockPos c : room) {
            boolean doorway = level.getBlockState(c).getBlock() instanceof DoorBlock
                    || level.getBlockState(c.above()).getBlock() instanceof DoorBlock;
            Direction openDir = null;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockState ns = level.getBlockState(c.relative(dir));
                if (ns.getBlock() instanceof DoorBlock || (ns.isAir() && !roomSet.contains(c.relative(dir)))) {
                    doorway = true;
                    openDir = dir;
                }
            }
            if (doorway) {
                blocked.add(c);
                if (openDir != null) blocked.add(c.relative(openDir.getOpposite())); // keep the path inside clear
            }
        }
        return blocked;
    }

    /** Place up to {@link #BEDS_PER_ROOM} beds, preferring beds whose head backs onto a wall. */
    private static int placeBeds(ServerLevel level, List<BlockPos> room, Set<BlockPos> roomSet, Set<BlockPos> blocked) {
        int placed = 0;
        Set<BlockPos> used = new HashSet<>();
        for (int pass = 0; pass < 2 && placed < BEDS_PER_ROOM; pass++) {
            boolean preferWall = pass == 0;
            for (BlockPos foot : room) {
                if (placed >= BEDS_PER_ROOM) break;
                if (blocked.contains(foot) || used.contains(foot) || !level.getBlockState(foot).isAir()) continue;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos head = foot.relative(dir);
                    if (!roomSet.contains(head) || blocked.contains(head) || used.contains(head)) continue;
                    if (!level.getBlockState(head).isAir()) continue;
                    if (preferWall && level.getBlockState(head.relative(dir)).isAir()) continue; // head not backed by wall
                    BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir);
                    level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
                    level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
                    used.add(foot);
                    used.add(head);
                    placed++;
                    break;
                }
            }
        }
        return placed;
    }
}
