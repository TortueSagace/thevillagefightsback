package com.example.villagefightsback.infection;

import com.example.villagefightsback.RichSheepEntity;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Sparse flora on freshly-converted fertile ground. After the infection fertilizes a block,
 * {@link #onConverted} gets ONE cheap, O(1)-ish chance to sprout something on a flat OPEN surface:
 *  - a small fertile tree (~0.2%), or
 *  - a fertile chorus flower (~0.3%) that then expands on its own (see FertileChorusFlowerBlock).
 *
 * Fertile lands stay MOSTLY OPEN, so both are deliberately rare. This is invoked from
 * {@code InfectionManager.convert(...)} once per successful conversion; it must never throw or scan widely
 * (that loop runs every 5 ticks over the whole frontier).
 */
public final class FertileFlora {
    private FertileFlora() {}

    // Per-conversion sprout chances. Tuned LOW so fertile ground stays open (see report notes).
    private static final double TREE_CHANCE = 0.002;   // ~0.2% per surface conversion -> a tree
    private static final double CHORUS_CHANCE = 0.003; // ~0.3% per surface conversion -> a chorus flower
    private static final float SHEEP_CHANCE = 0.08F;   // small chance of a Rich Sheep when a tree forms

    private static final int TRUNK_MIN = 4;
    private static final int TRUNK_MAX = 6;

    // Resolved lazily (blocks don't exist until registration completes), then cached PER COLOUR.
    //
    // This used to cache the three GREEN blocks once and use them everywhere, so a cyan or purple zone grew
    // green trees and green chorus standing in it -- the one place the colour was not carried through. The
    // zone's colour is already in scope at the call site and already threaded into every other write.
    private static final int LOG = 0, LEAVES = 1, CHORUS = 2;
    private static final java.util.Map<String, Block[]> BY_COLOUR = new java.util.HashMap<>();

    /**
     * {log, leaves, chorus flower} for one colour suffix ({@code ""} for green).
     *
     * <p>Each falls back to its green form if that colour is somehow missing, so a gap in the colour sets
     * degrades to the old behaviour rather than to no flora. The result is cached only once it actually
     * resolves, so a call made before registration finishes cannot poison the cache with nulls.
     */
    private static Block[] palette(String colour) {
        String c = colour == null ? "" : colour;
        Block[] cached = BY_COLOUR.get(c);
        if (cached != null) return cached;
        Block[] p = {
                or(byId("fertile_log" + c), byId("fertile_log")),
                or(byId("fertile_leaves" + c), byId("fertile_leaves")),
                or(byId("fertile_chorus_flower" + c), byId("fertile_chorus_flower")),
        };
        if (p[LOG] != null && p[LEAVES] != null) BY_COLOUR.put(c, p);
        return p;
    }

    private static Block or(Block coloured, Block green) {
        return coloured != null ? coloured : green;
    }

    private static Block byId(String name) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(TheVillageFightsBack.MODID, name));
    }

    /**
     * Called right after the infection converts a block to a fertile/rich block. With a SMALL chance, and only
     * when {@code converted} is a flat OPEN surface (air above, fertile/solid floor around it), sprouts ONE of
     * a fertile tree or a fertile chorus flower at {@code converted.above()}. Cheap (a handful of block reads)
     * and fully guarded so it can never throw / lag the infection loop.
     */
    public static void onConverted(ServerLevel level, BlockPos converted, StructureZones zones,
                                   MarkerZones marks, String colour) {
        if (level == null || converted == null) return;
        try {
            BlockPos up = converted.above();
            if (!level.isLoaded(up)) return;
            if (zones.contains(up)) return; // never sprout inside one of the village's own buildings
            if (marks.blocked(up.getX(), up.getY(), up.getZ())) return; // nor on ground a player has claimed
            // The conversion itself must be a SURFACE (open sky-step) -- air directly above the converted block.
            // (Plants standing on/near the new fertile soil are turned into fertile grass earlier, by
            //  InfectionManager.convertNearbyPlants, BEFORE the soil flips.)
            if (!level.getBlockState(up).isAir()) return;
            // Cheap "flat-ish ground" guard: the 4 horizontal neighbours of the converted block must be solid
            // (so we're not sprouting on a 1-wide ledge / cliff edge), and the cells above THEM must be open.
            if (!flatOpenSurface(level, converted)) return;

            RandomSource rnd = level.getRandom();
            double roll = rnd.nextDouble();
            if (roll < TREE_CHANCE) {
                tryGrowTree(level, up, zones, colour);
            } else if (roll < TREE_CHANCE + CHORUS_CHANCE) {
                tryPlaceChorus(level, up, colour); // `up` is already zone-checked above; writes only there
            }
        } catch (Throwable ignored) {
            // Flora is purely cosmetic; never let it break the infection tick.
        }
    }

    /** The converted block has a flat, open surface: its 4 horizontal neighbours are solid and open above them. */
    private static boolean flatOpenSurface(ServerLevel level, BlockPos floor) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = floor.relative(d);
            if (!level.isLoaded(side)) return false;
            if (!level.getBlockState(side).blocksMotion()) return false; // ground must continue (flat, not an edge)
            if (!level.getBlockState(side.above()).isAir()) return false; // and stay open beside us
        }
        return true;
    }

    /**
     * Build a small fertile tree at {@code pos} (the cell just ABOVE the converted ground): a 4-6 tall trunk of
     * fertile_log topped with a simple fertile_leaves blob, placed only into air. On success, gives a small
     * chance to spawn a Rich Sheep beside it.
     */
    public static void tryGrowTree(ServerLevel level, BlockPos pos, StructureZones zones, String colour) {
        Block[] pal = palette(colour);
        Block fertileLog = pal[LOG], fertileLeaves = pal[LEAVES];
        if (fertileLog == null || fertileLeaves == null) return;

        RandomSource rnd = level.getRandom();
        int trunk = TRUNK_MIN + rnd.nextInt(TRUNK_MAX - TRUNK_MIN + 1); // 4..6

        // Room check: the whole trunk column + a small canopy box must be air (cheap, bounded). A protected
        // building volume counts as "no room" too, so a tree next to a house can never push its canopy inside it.
        for (int dy = 0; dy < trunk + 2; dy++) {
            BlockPos t = pos.above(dy);
            if (!level.isLoaded(t) || !level.getBlockState(t).isAir()) return;
            if (zones.contains(t)) return;
        }
        int top = trunk - 1; // index of the highest trunk log
        // Canopy spans the top two trunk levels plus one above; verify those cells are loaded & air.
        for (int dy = top - 1; dy <= top + 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos l = pos.offset(dx, dy, dz);
                    if (!level.isLoaded(l)) return;
                    if (!level.getBlockState(l).isAir()) return;
                    if (zones.contains(l)) return;
                }
            }
        }

        // Trunk (axis-aware fertile log).
        BlockState log = fertileLog.defaultBlockState();
        if (fertileLog instanceof RotatedPillarBlock && log.hasProperty(BlockStateProperties.AXIS)) {
            log = log.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        }
        for (int dy = 0; dy < trunk; dy++) {
            level.setBlock(pos.above(dy), log, 3);
        }

        // Canopy: a simple leaves blob -- a 5x5 ring at the two top trunk levels (corners trimmed) and a small
        // 3x3 cap above, leaving the trunk core as log.
        BlockState leaves = fertileLeaves.defaultBlockState();
        placeLeafLayer(level, pos, top - 1, 2, leaves);
        placeLeafLayer(level, pos, top, 2, leaves);
        placeLeafLayer(level, pos, top + 1, 1, leaves);

        // A fertile tree occasionally brings a Rich Sheep (small chance).
        RichSheepEntity.trySpawn(level, pos, SHEEP_CHANCE);
    }

    /** Place a square leaf layer of the given radius around the trunk at trunk-relative height {@code dy},
     *  skipping the trunk core and trimming the far corners for a rounder blob. */
    private static void placeLeafLayer(ServerLevel level, BlockPos base, int dy, int radius, BlockState leaves) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;                       // keep the trunk core as log
                if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue; // trim corners -> round blob
                BlockPos l = base.offset(dx, dy, dz);
                if (level.isLoaded(l) && level.getBlockState(l).isAir()) {
                    level.setBlock(l, leaves, 3);
                }
            }
        }
    }

    /** Place a fertile chorus flower at {@code pos} (it then expands on its own) -- needs open space above. */
    private static void tryPlaceChorus(ServerLevel level, BlockPos pos, String colour) {
        Block fertileChorusFlower = palette(colour)[CHORUS];
        if (fertileChorusFlower == null) return;
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) return;
        BlockPos up = pos.above();
        if (!level.isLoaded(up) || !level.getBlockState(up).isAir()) return; // it grows upward -> need headroom
        level.setBlock(pos, fertileChorusFlower.defaultBlockState(), 3);
    }
}
