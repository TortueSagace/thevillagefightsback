package com.example.villagefightsback.infection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.common.Tags;

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
            "deepslate", "end_stone", "netherrack", "sculk", "leaves", "blackstone", "basalt",
            "soul_sand", "soul_soil", "snow_block", "gravel", "sand", "dirt", "log", "stone"
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
        BASE_HARDNESS.put("blackstone", 1.5F);
        BASE_HARDNESS.put("basalt", 1.25F);
        BASE_HARDNESS.put("soul_sand", 0.5F);
        BASE_HARDNESS.put("soul_soil", 0.5F);
        BASE_HARDNESS.put("snow_block", 0.2F);
    }

    // Lazily initialised (blocks don't exist until registration completes).
    private static Map<Block, String> exact;        // vanilla block -> base
    private static Map<String, Block> fertileByBase; // base -> our fertile block
    private static Map<String, Block> richByBase;    // base -> our rich block
    private static Set<Block> infectionBlocks;       // all fertile + rich + dirt_path
    /** Cached so the hot air-contact test compares by identity instead of calling a registry-holder getter. */
    private static Block fertileGrass;
    /** Sculk blocks the infection swallows as foliage. Registry-derived, so /reload can never stale it. */
    private static Set<Block> sculkPlants;
    /** Blocks that are neither vanilla nor ours, plus the sculk family. See {@link #isAlien}. */
    private static Set<Block> alienBlocks;
    private static Set<Block> richBlockSet;          // all rich blocks
    private static Set<Block> fertileCatalysts;      // fertile sculk catalysts, every colour
    private static Set<Block> richCatalysts;         // ...and the rich ones
    private static Set<Block> upgradableFertile;     // fertile blocks that have a rich counterpart (fertile -> rich)
    private static Set<Block> jobSites;              // villager workstation / meeting POI blocks -> never infected
    private static Set<Block> redstone;              // the redstone family -> never infected, never broken
    private static Set<Block> villagePaths;          // our coloured wall paths, in every colour

    /** Which colour set a block of ours belongs to; absent means green. See {@link #colourOf}. */
    private static Map<Block, String> colourByBlock;

    /** Every colour suffix, green first. Green is the empty string throughout. */
    static final String[] ALL_SUFFIXES;
    static {
        ALL_SUFFIXES = new String[ModBlocks.COLOUR_SUFFIXES.length + 1];
        ALL_SUFFIXES[0] = "";
        System.arraycopy(ModBlocks.COLOUR_SUFFIXES, 0, ALL_SUFFIXES, 1, ModBlocks.COLOUR_SUFFIXES.length);
    }

    private static Block byId(String name) {
        return BuiltInRegistries.BLOCK.get(TheVillageFightsBack.id(name));
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
        upgradableFertile = new HashSet<>();
        colourByBlock = new HashMap<>();
        // Keys are "base" for green and "base_cyan" etc. for a colour, so one map serves all five sets and
        // convertedState just appends the colour it was asked for.
        for (String c : ALL_SUFFIXES) {
            for (String b : BASES) {
                Block f = byId("fertile_" + b + c), r = byId("rich_" + b + c);
                if (f != null) {
                    fertileByBase.put(b + c, f); infectionBlocks.add(f); colourByBlock.put(f, c);
                }
                if (r != null) {
                    richByBase.put(b + c, r); infectionBlocks.add(r); richBlockSet.add(r); colourByBlock.put(r, c);
                }
                if (f != null && r != null) upgradableFertile.add(f); // can ripen into its rich form
            }
        }
        // Every OTHER coloured block (stairs, grass, kelp, path, planks, chorus, catalysts, door). They are all
        // ours by construction, so this replaces what would otherwise be 18 hard-coded lines per colour.
        for (java.util.Map.Entry<String, net.neoforged.neoforge.registries.DeferredHolder<Block, Block>> e
                : ModBlocks.COLOURED.entrySet()) {
            Block b = e.getValue().get();
            infectionBlocks.add(b);
            if (e.getKey().startsWith("rich_")) richBlockSet.add(b);
            for (String c : ModBlocks.COLOUR_SUFFIXES) {
                if (e.getKey().endsWith(c)) { colourByBlock.putIfAbsent(b, c); break; }
            }
        }
        // The sculk catalysts, by colour, so a bloom recognises a cyan catalyst as readily as a green one.
        // A Set lookup keeps the check cheap enough for the block scan SculkCatalystEvents runs on a death.
        fertileCatalysts = new HashSet<>();
        richCatalysts = new HashSet<>();
        for (String c : ALL_SUFFIXES) {
            Block f = byId("fertile_sculk_catalyst" + c), r = byId("rich_sculk_catalyst" + c);
            if (f != null) fertileCatalysts.add(f);
            if (r != null) richCatalysts.add(r);
        }

        // The village's coloured paths, by colour, so isPath can answer for all five without naming them.
        villagePaths = new HashSet<>();
        for (String c : ALL_SUFFIXES) {
            Block vp = byId("village_path" + c);
            if (vp != null) villagePaths.add(vp);
        }

        infectionBlocks.add(Blocks.DIRT_PATH);
        if (ModBlocks.INFESTED_PATH.isBound()) infectionBlocks.add(ModBlocks.INFESTED_PATH.get()); // slow emerald path
        if (ModBlocks.VILLAGE_PATH.isBound()) infectionBlocks.add(ModBlocks.VILLAGE_PATH.get());  // the village's own
        if (ModBlocks.FERTILE_KELP.isBound()) infectionBlocks.add(ModBlocks.FERTILE_KELP.get());   // converted kelp

        // The redstone family: never converted, so a player's contraption survives the village growing over it.
        //
        // Most of these are non-solid and already fall out of isCandidate's blocksMotion test -- listing them
        // is belt and braces, and it is also what lets convert() ASK "is there redstone above this cell", which
        // is the case that actually mattered: converting the block under dust or a repeater used to pop the
        // component off. See InfectionManager.convert.
        redstone = new HashSet<>();
        for (Block b : new Block[]{
                Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_WIRE, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
                Blocks.REPEATER, Blocks.COMPARATOR, Blocks.OBSERVER, Blocks.TARGET, Blocks.REDSTONE_LAMP,
                Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.PISTON_HEAD, Blocks.MOVING_PISTON,
                Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER, Blocks.NOTE_BLOCK, Blocks.LEVER,
                Blocks.DAYLIGHT_DETECTOR, Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK, Blocks.LIGHTNING_ROD,
                Blocks.SCULK_SENSOR, Blocks.CALIBRATED_SCULK_SENSOR,
                Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL}) {
            redstone.add(b);
        }

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
        if (ModBlocks.FERTILE_STAIRS.isBound()) infectionBlocks.add(ModBlocks.FERTILE_STAIRS.get());
        if (ModBlocks.FERTILE_SLAB.isBound()) infectionBlocks.add(ModBlocks.FERTILE_SLAB.get());
        if (ModBlocks.FERTILE_FENCE.isBound()) infectionBlocks.add(ModBlocks.FERTILE_FENCE.get());
        if (ModBlocks.FERTILE_IRON_BARS.isBound()) infectionBlocks.add(ModBlocks.FERTILE_IRON_BARS.get());
        if (ModBlocks.FERTILE_DOOR.isBound()) infectionBlocks.add(ModBlocks.FERTILE_DOOR.get());
        if (ModBlocks.FERTILE_PLANKS.isBound()) infectionBlocks.add(ModBlocks.FERTILE_PLANKS.get());
        if (ModBlocks.FERTILE_PLANKS_STAIRS.isBound()) infectionBlocks.add(ModBlocks.FERTILE_PLANKS_STAIRS.get());
        if (ModBlocks.FERTILE_PLANKS_SLAB.isBound()) infectionBlocks.add(ModBlocks.FERTILE_PLANKS_SLAB.get());
        if (ModBlocks.FERTILE_COBBLESTONE.isBound()) infectionBlocks.add(ModBlocks.FERTILE_COBBLESTONE.get());
        // fertile + rich chorus blocks count as infection so they are never re-converted.
        if (ModBlocks.FERTILE_CHORUS_PLANT.isBound()) infectionBlocks.add(ModBlocks.FERTILE_CHORUS_PLANT.get());
        if (ModBlocks.FERTILE_CHORUS_FLOWER.isBound()) infectionBlocks.add(ModBlocks.FERTILE_CHORUS_FLOWER.get());
        if (ModBlocks.RICH_CHORUS_PLANT.isBound()) {
            Block b = ModBlocks.RICH_CHORUS_PLANT.get();
            infectionBlocks.add(b); richBlockSet.add(b);
        }
        if (ModBlocks.RICH_CHORUS_FLOWER.isBound()) {
            Block b = ModBlocks.RICH_CHORUS_FLOWER.get();
            infectionBlocks.add(b); richBlockSet.add(b);
        }

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
        putAll(exact, "blackstone", Blocks.BLACKSTONE); // polished/gilded/chiseled caught by id-substring
        putAll(exact, "basalt", Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT);
        putAll(exact, "soul_sand", Blocks.SOUL_SAND);
        putAll(exact, "soul_soil", Blocks.SOUL_SOIL);
        putAll(exact, "snow_block", Blocks.SNOW_BLOCK);
        // logs are caught by id-substring ("oak_log", "spruce_log", ...).
        fertileGrass = com.example.villagefightsback.ModBlocks.FERTILE_GRASS.get();
        // A sculk sensor is not a plant by any reasonable reading, but it behaves like one here: a thin growth
        // on the ground the green is supposed to swallow. It reaches fertile grass through the plant channel
        // below rather than the main conversion path, which is why the light-emission gate in isCandidate is
        // untouched -- opening THAT would let torches and lanterns through with it.
        sculkPlants = java.util.Set.of(Blocks.SCULK_SENSOR, Blocks.CALIBRATED_SCULK_SENSOR);
        // Built once from the REGISTRY, which is why caching it is safe where caching a tag set is not:
        // registry contents are frozen before this runs and /reload cannot change them, whereas tags rebind
        // on every datapack reload.
        alienBlocks = new java.util.HashSet<>();
        for (Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            net.minecraft.resources.ResourceLocation id =
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
            if (id == null) continue;
            String ns = id.getNamespace();
            if (!"minecraft".equals(ns) && !TheVillageFightsBack.MODID.equals(ns)) alienBlocks.add(b);
        }
        // Sculk counts as alien wherever it is found, including inside a house: it is the one vanilla family
        // that reads as an infection of its own, and the village has no reason to shelter it.
        java.util.Collections.addAll(alienBlocks, Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST,
                Blocks.SCULK_SHRIEKER, Blocks.SCULK_SENSOR, Blocks.CALIBRATED_SCULK_SENSOR);
    }

    /** True if {@code s} is one of our infection blocks (any fertile/rich) or a dirt-path vector. */
    /**
     * Which colour set {@code s} belongs to, as the registry-name suffix ("" for green).
     *
     * <p>This is the whole colour-propagation mechanism. A conversion asks the block it is spreading FROM what
     * colour it is and makes the new block match, so a zone stays one colour without anything tracking it. A
     * block that is not ours -- a brain node seeding its first cell -- answers "", which is exactly the rule
     * that the brain node always spreads green.
     */
    public static String colourOf(BlockState s) {
        init();
        // Every conversion reads this to inherit its source's colour, so answering green here is the whole of
        // the monogreen switch on the propagation side: existing coloured blocks stay exactly as they are, but
        // nothing new grows from them in that colour.
        if (com.example.villagefightsback.Config.monochromeGreen()) return "";
        return colourByBlock.getOrDefault(s.getBlock(), "");
    }

    public static boolean isInfection(BlockState s) {
        init();
        return infectionBlocks.contains(s.getBlock());
    }

    /**
     * A path the infection can grow from: a vanilla dirt path, or one of the village's own coloured paths.
     *
     * <p>The village paths have to be here. A path head whose block does not answer this is dropped from the
     * frontier on the next pass, so laying a coloured path along the wall and NOT listing it would have made
     * the whole wall ring inert -- the ring is exactly what those paths are for.
     */
    public static boolean isPath(BlockState s) {
        init();
        return s.is(Blocks.DIRT_PATH) || villagePaths.contains(s.getBlock());
    }

    /** A "fertilized" block that counts toward the footprint cap: any fertile/rich/shaped/door block, but NOT
     *  a dirt-path vector (paths are just spread routes -- and plain dirt paths also occur naturally). */
    public static boolean isConverted(BlockState s) {
        init();
        if (!infectionBlocks.contains(s.getBlock())) return false;
        if (s.is(Blocks.DIRT_PATH)) return false;
        if (villagePaths.contains(s.getBlock())) return false;   // a path vector, like the two above
        return !(ModBlocks.INFESTED_PATH.isBound() && s.is(ModBlocks.INFESTED_PATH.get())); // path vector, not a tally block
    }

    public static boolean isRich(BlockState s) {
        init();
        return richBlockSet.contains(s.getBlock());
    }

    /** A fertile block that has a rich counterpart -- the spread may "ripen" it from fertile into rich. */
    public static boolean isFertileUpgradable(BlockState s) {
        init();
        return upgradableFertile.contains(s.getBlock());
    }

    /**
     * Is this block foreign to both vanilla and this mod?
     *
     * <p>Asked only for a block that sits INSIDE a protected building, where it is the one thing the village
     * will still digest. A house full of oak planks and our own blocks stays untouched; something a third
     * mod put there does not get to shelter behind the village's walls.
     *
     * <p>One hash lookup. See {@code init()} for why the set is safe to cache.
     */
    public static boolean isAlien(BlockState s) {
        init();
        return alienBlocks.contains(s.getBlock());
    }

    /** A small decorative plant the infection turns into fertile grass when fertile blocks appear next to it:
     *  short grass, fern, small flowers, dead bush, and the LOWER half of tall (double) plants -- converting the
     *  lower half auto-removes the upper half via vanilla shape updates. NOT crops/saplings/sugar cane. */
    public static boolean isConvertiblePlant(BlockState s) {
        init();
        Block b = s.getBlock();
        // Already ours. FertileGrassBlock extends BushBlock, so without this the widened test below would
        // match our own grass and re-place every blade of it in range of every conversion, forever.
        if (b == fertileGrass || isInfection(s)) return false;
        // Sculk sensors, before the solidity fast-out: a sensor is solid and would die on the next line.
        if (sculkPlants.contains(b)) return true;
        // THE FAST-OUT, and what makes the widening free. Almost every neighbour is air or a solid block and
        // both end here; what survives is the small set of non-air, non-collidable cells.
        if (s.isAir() || s.blocksMotion()) return false;
        if (isFarmed(s)) return false;
        if (b instanceof net.minecraft.world.level.block.DoublePlantBlock) {
            return s.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
        }
        // BushBlock catches most modded foliage; SWORD_EFFICIENT is the net for the mods that extend Block
        // directly. Asked per state rather than cached, because a tag set built once in init() would go
        // stale on /reload and this is the one thing here that can change at runtime.
        return b instanceof net.minecraft.world.level.block.BushBlock
                || s.is(net.minecraft.tags.BlockTags.SWORD_EFFICIENT);
    }

    /**
     * A plant somebody is growing on purpose. The green leaves these alone.
     *
     * <p>An explicit list rather than a tag guess, because the two that matter most are traps: sweet berry
     * bushes and saplings both extend {@code BushBlock}, so the widened predicate above would eat a player's
     * orchard and every sapling in the world without this. Seagrass is here for a different reason -- fertile
     * grass is not waterloggable, so converting it would drain the water cell it stands in.
     */
    private static boolean isFarmed(BlockState s) {
        Block b = s.getBlock();
        return b instanceof net.minecraft.world.level.block.CropBlock
                || b instanceof net.minecraft.world.level.block.StemBlock
                || b instanceof net.minecraft.world.level.block.AttachedStemBlock
                || b instanceof net.minecraft.world.level.block.SaplingBlock
                || b instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                || b instanceof net.minecraft.world.level.block.SugarCaneBlock
                || b instanceof net.minecraft.world.level.block.NetherWartBlock
                || b instanceof net.minecraft.world.level.block.CocoaBlock
                || b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS
                || s.is(net.minecraft.tags.BlockTags.CROPS)
                || s.is(net.minecraft.tags.BlockTags.SAPLINGS);
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
        if (b == Blocks.POWDER_SNOW) return false;                     // powder snow is immune to conversion
        if (b == Blocks.FARMLAND) return false;
        // Crafting tables and furnaces ARE convertible (they're not villager job sites). Allowed explicitly,
        // before the light-emission rule below, so even a lit/working furnace gets taken over.
        if (b == Blocks.CRAFTING_TABLE || b == Blocks.FURNACE) return hasAirContact(level, pos);
        if (jobSites.contains(b)) return false;                        // keep village jobs (workstations + bell)
        if (isRedstone(s)) return false;                               // never eat a contraption
        if (b instanceof net.minecraft.world.level.block.ChestBlock
                || b instanceof net.minecraft.world.level.block.EnderChestBlock
                || b instanceof net.minecraft.world.level.block.BedBlock) return false; // keep chests & beds
        if (b == Blocks.BOOKSHELF || b == Blocks.CHISELED_BOOKSHELF) return false; // keep bookshelves
        if (s.is(BlockTags.WOOL)) return false;                        // keep wool (all colours)
        if (s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)) return false; // keep glass + panes
        if (s.is(BlockTags.STONE_BRICKS)) return false;                // keep stone bricks (+ chiseled/mossy/cracked)
        if (b instanceof net.minecraft.world.level.block.CampfireBlock) return false; // keep campfires (lit OR unlit)
        // Food is never infected: wheat & other crops, their stems, and hay (a wheat block).
        if (b == Blocks.HAY_BLOCK
                || b instanceof net.minecraft.world.level.block.CropBlock
                || b instanceof net.minecraft.world.level.block.StemBlock
                || s.is(BlockTags.CROPS)) return false;
        if (s.is(BlockTags.PLANKS)) return false;
        if (s.is(Tags.Blocks.ORES) || s.is(Tags.Blocks.STORAGE_BLOCKS)) return false; // ores + resource blocks
        if (s.getLightEmission() > 0) return false;                    // any light source (torch/glowstone/lantern/...)
        if (b.getExplosionResistance() >= 1200.0F) return false;       // toughness must be < 1200
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (id != null) {
            // Never eat our own special blocks (brainNode / walls' fake cobblestone / glass / scaffolds / ...).
            if (id.getNamespace().equals(TheVillageFightsBack.MODID)) return false;
            String path = id.getPath();
            // cobblestone (incl. mossy/stairs/slabs/walls -> protects the village wall) and any brick block.
            if (path.contains("cobblestone") || path.contains("brick")) return false;
            // every quartz block variant is immune (quartz_block, smooth/chiseled quartz, quartz_pillar,
            // quartz_stairs, quartz_slab, ...). quartz_bricks is already caught by the "brick" rule above.
            if (path.contains("quartz")) return false;
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

    /**
     * Counts as "contact with a non-solid cell": air, any fluid, a thin snow/carpet layer -- or our own
     * fertile grass.
     *
     * <p>That last one closes a blind spot that made the infection look broken. Fertile grass is a
     * no-collision bush, so a block with grass on top and soil all around had no air contact at all and could
     * never be converted -- and {@code FertileGrassBlock.randomTick}, which exists precisely to fertilize the
     * soil beneath a plant, went through the same gate and therefore no-opped forever on flat ground. Every
     * meadow the green reached grew a permanent lid over itself.
     *
     * <p>It belongs on this list on its own merits: snow layer and carpet are here because they are thin,
     * walk-through, non-solid cells, and fertile grass is one too.
     */
    private static boolean isAirContact(BlockState ns) {
        return ns.isAir() || !ns.getFluidState().isEmpty()
                || ns.is(Blocks.SNOW)                                   // the snow LAYER (not snow_block)
                || ns.getBlock() == fertileGrass
                || ns.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock;
    }

    /** The base name a candidate maps to (exact correspondence, then id-substring, then closest hardness). */
    public static String baseFor(BlockState s) {
        init();
        Block b = s.getBlock();
        String ex = exact.get(b);
        if (ex != null) return ex;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
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
        return convertedState(source, richVariant, "");
    }

    /** As above, but in {@code colour} (a suffix from {@link #colourOf}; "" is green). */
    public static BlockState convertedState(BlockState source, boolean richVariant, String colour) {
        init();
        BlockState shaped = shapedConversion(source, colour); // stairs/slabs/fences keep their shape
        if (shaped != null) return shaped;
        String base = baseFor(source);
        Map<String, Block> by = richVariant ? richByBase : fertileByBase;
        Block target = by.get(base + colour);
        if (target == null) target = by.get("stone" + colour);
        if (target == null) target = by.get(base);          // colour missing entirely -> fall back to green
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
    /** The coloured twin of a shape-preserving block, falling back to the green one if that colour is absent. */
    private static Block shaped(String greenName, String colour,
                                net.neoforged.neoforge.registries.DeferredHolder<Block, Block> green) {
        if (!colour.isEmpty()) {
            net.neoforged.neoforge.registries.DeferredHolder<Block, Block> c =
                    ModBlocks.COLOURED.get(greenName + colour);
            if (c != null) return c.get();
        }
        return green.get();
    }

    private static BlockState shapedConversion(BlockState source, String colour) {
        Block b = source.getBlock();
        Block target;
        Property<?>[] props;
        if (b instanceof StairBlock) {
            target = shaped("fertile_stairs", colour, ModBlocks.FERTILE_STAIRS);
            props = new Property<?>[]{BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.HALF,
                    BlockStateProperties.STAIRS_SHAPE, BlockStateProperties.WATERLOGGED};
        } else if (b instanceof SlabBlock) {
            target = shaped("fertile_slab", colour, ModBlocks.FERTILE_SLAB);
            props = new Property<?>[]{BlockStateProperties.SLAB_TYPE, BlockStateProperties.WATERLOGGED};
        } else if (b instanceof FenceBlock) {
            target = shaped("fertile_fence", colour, ModBlocks.FERTILE_FENCE);
            props = new Property<?>[]{BlockStateProperties.NORTH, BlockStateProperties.EAST,
                    BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.WATERLOGGED};
        } else if (b == Blocks.IRON_BARS) {
            target = shaped("fertile_iron_bars", colour, ModBlocks.FERTILE_IRON_BARS);
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
        return sculk(rich, "");
    }

    /** Our fertile (or rich) sculk in {@code colour}, falling back to green if that colour is missing. */
    public static Block sculk(boolean rich, String colour) {
        init();
        Map<String, Block> by = rich ? richByBase : fertileByBase;
        Block b = by.get("sculk" + (colour == null ? "" : colour));
        return b != null ? b : by.get("sculk");
    }

    /**
     * Part of the redstone family: a component, or a block that carries or is wired into a circuit.
     *
     * <p>Also matches by class for the families with a block per variant (buttons, pressure plates), so a
     * modded or newly added one is covered without listing it.
     */
    public static boolean isRedstone(BlockState s) {
        init();
        Block b = s.getBlock();
        return redstone.contains(b)
                || b instanceof net.minecraft.world.level.block.ButtonBlock
                || b instanceof net.minecraft.world.level.block.BasePressurePlateBlock
                || b instanceof net.minecraft.world.level.block.DiodeBlock
                || b instanceof net.minecraft.world.level.block.RedStoneWireBlock
                || b instanceof net.minecraft.world.level.block.BaseRailBlock;
    }

    /** A fertile sculk catalyst of ANY colour. */
    public static boolean isFertileSculkCatalyst(BlockState s) {
        init();
        return fertileCatalysts.contains(s.getBlock());
    }

    /** A rich sculk catalyst of ANY colour. */
    public static boolean isRichSculkCatalyst(BlockState s) {
        init();
        return richCatalysts.contains(s.getBlock());
    }

    /** Ground a dirt-path may spread across: vanilla dirt-family / grass only. NOT our fertile blocks (paths
     *  must never overwrite fertilized ground) and not existing paths. */
    public static boolean isPathGround(BlockState s) {
        init();
        Block b = s.getBlock();
        if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL
                || b == Blocks.MYCELIUM || b == Blocks.ROOTED_DIRT || b == Blocks.MUD) return true;
        return false; // paths spread only over vanilla dirt-like ground -- they never overwrite our fertile blocks
    }
}
