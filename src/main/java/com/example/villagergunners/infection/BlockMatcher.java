package com.example.villagergunners.infection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.villagergunners.VillagerGunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Decides which world blocks the infection may convert, and what Fertile/Rich block each becomes.
 *
 * A candidate is mapped to a base ("dirt", "stone", "deepslate", ...) by, in order: a direct vanilla
 * correspondence; else a substring of the block's id (so "red_sculk"/"polished_deepslate" map too); else
 * the base whose hardness is closest (so arbitrary modded blocks still infect). Lookups of our own
 * fertile_/rich_ blocks are resolved by id lazily (after registration) and cached.
 */
public final class BlockMatcher {
    private BlockMatcher() {}

    /** Base names, MOST-SPECIFIC FIRST so id-substring matching prefers "end_stone"/"deepslate" over "stone". */
    static final String[] BASES = {
            "deepslate", "end_stone", "netherrack", "sculk", "gravel", "sand", "dirt", "log", "stone"
    };

    /** Reference hardness per base (vanilla destroy time) for the closest-toughness fallback. */
    private static final Map<String, Float> BASE_HARDNESS = new HashMap<>();
    static {
        BASE_HARDNESS.put("dirt", 0.5F);
        BASE_HARDNESS.put("sand", 0.5F);
        BASE_HARDNESS.put("gravel", 0.6F);
        BASE_HARDNESS.put("stone", 1.5F);
        BASE_HARDNESS.put("deepslate", 3.0F);
        BASE_HARDNESS.put("netherrack", 0.4F);
        BASE_HARDNESS.put("end_stone", 3.0F);
        BASE_HARDNESS.put("sculk", 0.2F);
        BASE_HARDNESS.put("log", 2.0F);
    }

    // Lazily initialised (blocks don't exist until registration completes).
    private static Map<Block, String> exact;        // vanilla block -> base
    private static Map<String, Block> fertileByBase; // base -> our fertile block
    private static Map<String, Block> richByBase;    // base -> our rich block
    private static Set<Block> infectionBlocks;       // all fertile + rich + dirt_path
    private static Set<Block> richBlockSet;          // all rich blocks

    private static Block byId(String name) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(VillagerGunners.MODID, name));
    }

    private static void putAll(Map<Block, String> m, String base, Block... blocks) {
        for (Block b : blocks) if (b != null) m.put(b, base);
    }

    private static void init() {
        if (fertileByBase != null) return;
        fertileByBase = new HashMap<>();
        richByBase = new HashMap<>();
        infectionBlocks = new HashSet<>();
        richBlockSet = new HashSet<>();
        for (String b : BASES) {
            Block f = byId("fertile_" + b), r = byId("rich_" + b);
            if (f != null) { fertileByBase.put(b, f); infectionBlocks.add(f); }
            if (r != null) { richByBase.put(b, r); infectionBlocks.add(r); richBlockSet.add(r); }
        }
        infectionBlocks.add(Blocks.DIRT_PATH);

        exact = new HashMap<>();
        putAll(exact, "dirt", Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL,
                Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.MUD);
        putAll(exact, "sand", Blocks.SAND, Blocks.RED_SAND);
        putAll(exact, "gravel", Blocks.GRAVEL);
        putAll(exact, "stone", Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
                Blocks.TUFF, Blocks.CALCITE);
        putAll(exact, "deepslate", Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE);
        putAll(exact, "netherrack", Blocks.NETHERRACK, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM);
        putAll(exact, "end_stone", Blocks.END_STONE);
        putAll(exact, "sculk", Blocks.SCULK);
        // logs are caught by id-substring ("oak_log", "spruce_log", ...).
    }

    /** True if {@code s} is one of our infection blocks (any fertile/rich) or a dirt-path vector. */
    public static boolean isInfection(BlockState s) {
        init();
        return infectionBlocks.contains(s.getBlock());
    }

    public static boolean isPath(BlockState s) {
        return s.is(Blocks.DIRT_PATH);
    }

    public static boolean isRich(BlockState s) {
        init();
        return richBlockSet.contains(s.getBlock());
    }

    /**
     * Whether the block at {@code pos} may be converted to a fertile/rich block: a solid, non-fluid block
     * that is NOT a path/cobblestone/farmland/planks/ore/resource-block/brick/bright-light source, is not
     * already ours, has explosion resistance &lt; 1200, and touches at least one non-solid cell (air/fluid).
     */
    public static boolean isCandidate(LevelReader level, BlockPos pos, BlockState s) {
        init();
        if (s.isAir() || !s.getFluidState().isEmpty() || !s.blocksMotion()) return false; // must be solid
        Block b = s.getBlock();
        if (infectionBlocks.contains(b)) return false;                 // already part of our extension
        if (b == Blocks.FARMLAND) return false;
        if (s.is(BlockTags.PLANKS)) return false;
        if (s.is(Tags.Blocks.ORES) || s.is(Tags.Blocks.STORAGE_BLOCKS)) return false; // ores + resource blocks
        if (s.getLightEmission() > 7) return false;                    // brighter than a redstone torch
        if (b.getExplosionResistance() >= 1200.0F) return false;       // toughness must be < 1200
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        if (id != null) {
            // Never eat our own special blocks (gravemind / walls' fake cobblestone / glass / scaffolds / ...).
            if (id.getNamespace().equals(VillagerGunners.MODID)) return false;
            String path = id.getPath();
            // cobblestone (incl. mossy/stairs/slabs/walls -> protects the village wall) and any brick block.
            if (path.contains("cobblestone") || path.contains("brick")) return false;
        }
        for (Direction d : Direction.values()) {                       // must touch a non-solid cell
            BlockState ns = level.getBlockState(pos.relative(d));
            if (isAirContact(ns)) return true;
        }
        return false;
    }

    /** Counts as "contact with a non-solid cell": air, any fluid, or a thin snow/carpet layer (spec). */
    private static boolean isAirContact(BlockState ns) {
        return ns.isAir() || !ns.getFluidState().isEmpty()
                || ns.is(Blocks.SNOW)                                   // the snow LAYER (not snow_block)
                || ns.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock;
    }

    /** The base name a candidate maps to (exact correspondence, then id-substring, then closest hardness). */
    public static String baseFor(BlockState s) {
        init();
        Block b = s.getBlock();
        String ex = exact.get(b);
        if (ex != null) return ex;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        String path = id != null ? id.getPath() : "";
        for (String base : BASES) if (path.contains(base)) return base;
        float h = b.defaultDestroyTime();
        String best = "stone";
        float bestDelta = Float.MAX_VALUE;
        for (Map.Entry<String, Float> e : BASE_HARDNESS.entrySet()) {
            float delta = Math.abs(e.getValue() - h);
            if (delta < bestDelta) { bestDelta = delta; best = e.getKey(); }
        }
        return best;
    }

    /** The fertile (or rich) blockstate a candidate should become, preserving pillar AXIS where applicable. */
    public static BlockState convertedState(BlockState source, boolean richVariant) {
        init();
        String base = baseFor(source);
        Block target = (richVariant ? richByBase : fertileByBase).get(base);
        if (target == null) target = (richVariant ? richByBase : fertileByBase).get("stone");
        if (target == null) return null;
        BlockState out = target.defaultBlockState();
        if (target instanceof RotatedPillarBlock && source.hasProperty(BlockStateProperties.AXIS)
                && out.hasProperty(BlockStateProperties.AXIS)) {
            out = out.setValue(BlockStateProperties.AXIS, source.getValue(BlockStateProperties.AXIS));
        }
        return out;
    }

    public static Block fertileSand() {
        init();
        return fertileByBase.get("sand");
    }

    /** Ground a dirt-path may spread across (dirt-family / grass / our fertile dirt). Excludes existing paths. */
    public static boolean isPathGround(BlockState s) {
        init();
        Block b = s.getBlock();
        if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL
                || b == Blocks.MYCELIUM || b == Blocks.ROOTED_DIRT || b == Blocks.MUD) return true;
        return b == fertileByBase.get("dirt");
    }
}
