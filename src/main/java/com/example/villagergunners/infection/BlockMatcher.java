package com.example.villagergunners.infection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.villagergunners.ModBlocks;
import com.example.villagergunners.VillagerGunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
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
            "deepslate", "end_stone", "netherrack", "sculk", "leaves", "gravel", "sand", "dirt", "log", "stone"
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
        BASE_HARDNESS.put("leaves", 0.2F);
    }

    // Lazily initialised (blocks don't exist until registration completes).
    private static Map<Block, String> exact;        // vanilla block -> base
    private static Map<String, Block> fertileByBase; // base -> our fertile block
    private static Map<String, Block> richByBase;    // base -> our rich block
    private static Set<Block> infectionBlocks;       // all fertile + rich + dirt_path
    private static Set<Block> richBlockSet;          // all rich blocks
    private static Set<Block> jobSites;              // villager workstation / meeting POI blocks -> never infected

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

        // Villager job-site workstations + the meeting bell: never infected, so a village keeps its jobs.
        jobSites = new HashSet<>();
        for (Block b : new Block[]{
                Blocks.BELL, Blocks.LECTERN, Blocks.COMPOSTER, Blocks.BARREL, Blocks.BLAST_FURNACE,
                Blocks.SMOKER, Blocks.BREWING_STAND, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE,
                Blocks.GRINDSTONE, Blocks.LOOM, Blocks.SMITHING_TABLE, Blocks.STONECUTTER,
                Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.LAVA_CAULDRON, Blocks.POWDER_SNOW_CAULDRON}) {
            jobSites.add(b);
        }
        // our shape-preserving fertile blocks count as infection too, so they are never re-converted to cubes.
        if (ModBlocks.FERTILE_STAIRS.isPresent()) infectionBlocks.add(ModBlocks.FERTILE_STAIRS.get());
        if (ModBlocks.FERTILE_SLAB.isPresent()) infectionBlocks.add(ModBlocks.FERTILE_SLAB.get());
        if (ModBlocks.FERTILE_FENCE.isPresent()) infectionBlocks.add(ModBlocks.FERTILE_FENCE.get());
        if (ModBlocks.FERTILE_DOOR.isPresent()) infectionBlocks.add(ModBlocks.FERTILE_DOOR.get());

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

    /** A "fertilized" block that counts toward the footprint cap: any fertile/rich/shaped/door block, but NOT
     *  a dirt-path vector (paths are just spread routes -- and plain dirt paths also occur naturally). */
    public static boolean isConverted(BlockState s) {
        init();
        return infectionBlocks.contains(s.getBlock()) && !s.is(Blocks.DIRT_PATH);
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
        Block b = s.getBlock();
        if (infectionBlocks.contains(b)) return false;                 // already part of our extension
        if (b == Blocks.IRON_DOOR || b == Blocks.IRON_TRAPDOOR) return false; // iron doors are immune
        // Wooden doors convert to the fertile door even when open (no solid collision then) -> handle first.
        if (b instanceof net.minecraft.world.level.block.DoorBlock) return hasAirContact(level, pos);
        if (s.isAir() || !s.getFluidState().isEmpty() || !s.blocksMotion()) return false; // must be solid
        if (b == Blocks.FARMLAND) return false;
        if (jobSites.contains(b)) return false;                        // keep village jobs (workstations + bell)
        if (b instanceof net.minecraft.world.level.block.ChestBlock
                || b instanceof net.minecraft.world.level.block.EnderChestBlock
                || b instanceof net.minecraft.world.level.block.BedBlock) return false; // keep chests & beds
        if (b == Blocks.BOOKSHELF || b == Blocks.CHISELED_BOOKSHELF) return false; // keep bookshelves
        // Food is never infected: wheat & other crops, their stems, and hay (a wheat block).
        if (b == Blocks.HAY_BLOCK
                || b instanceof net.minecraft.world.level.block.CropBlock
                || b instanceof net.minecraft.world.level.block.StemBlock
                || s.is(BlockTags.CROPS)) return false;
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
        return hasAirContact(level, pos);
    }

    /** True if any of the 6 face neighbours of {@code pos} is a non-solid cell (air/fluid/snow/carpet). */
    private static boolean hasAirContact(LevelReader level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (isAirContact(level.getBlockState(pos.relative(d)))) return true;
        }
        return false;
    }

    /** A wooden (non-iron) door -- converts to the fertile door; iron doors are filtered out earlier. */
    public static boolean isWoodenDoor(BlockState s) {
        init();
        Block b = s.getBlock();
        return b instanceof net.minecraft.world.level.block.DoorBlock
                && b != Blocks.IRON_DOOR && !infectionBlocks.contains(b);
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
        BlockState shaped = shapedConversion(source); // stairs/slabs/fences keep their shape (not a solid cube)
        if (shaped != null) return shaped;
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

    /**
     * Stairs, slabs and fences convert to a fertile block of the SAME shape (instead of a solid cube), so the
     * infection doesn't fill the cell and wreck the structure's geometry. Returns null for any other block.
     */
    private static BlockState shapedConversion(BlockState source) {
        Block b = source.getBlock();
        Block target;
        Property<?>[] props;
        if (b instanceof StairBlock) {
            target = ModBlocks.FERTILE_STAIRS.get();
            props = new Property<?>[]{BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF,
                    BlockStateProperties.STAIRS_SHAPE, BlockStateProperties.WATERLOGGED};
        } else if (b instanceof SlabBlock) {
            target = ModBlocks.FERTILE_SLAB.get();
            props = new Property<?>[]{BlockStateProperties.SLAB_TYPE, BlockStateProperties.WATERLOGGED};
        } else if (b instanceof FenceBlock) {
            target = ModBlocks.FERTILE_FENCE.get();
            props = new Property<?>[]{BlockStateProperties.NORTH, BlockStateProperties.EAST,
                    BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.WATERLOGGED};
        } else {
            return null;
        }
        if (target == null) return null;
        BlockState out = target.defaultBlockState();
        for (Property<?> p : props) out = copyProp(source, out, p);
        return out;
    }

    /** Copy property {@code p}'s value from source to out, if both states have it. */
    private static <T extends Comparable<T>> BlockState copyProp(BlockState src, BlockState out, Property<T> p) {
        return (src.hasProperty(p) && out.hasProperty(p)) ? out.setValue(p, src.getValue(p)) : out;
    }

    public static Block fertileSand() {
        init();
        return fertileByBase.get("sand");
    }

    /** Our fertile (or rich) sculk block -- what the matching sculk catalyst spreads. */
    public static Block sculk(boolean rich) {
        init();
        return (rich ? richByBase : fertileByBase).get("sculk");
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
