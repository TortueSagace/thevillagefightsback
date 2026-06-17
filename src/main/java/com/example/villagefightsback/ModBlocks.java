package com.example.villagefightsback;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block/item/creative-tab registration. */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TheVillageFightsBack.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TheVillageFightsBack.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TheVillageFightsBack.MODID);

    /**
     * The Village Brain Node: a recolored respawn-anchor look-alike that drives the village's global
     * evolution level. It glows brighter as the level rises (charges visual). As tough as obsidian
     * (hardness 50, blast resistance 1200) so explosions can't destroy it.
     */
    public static final RegistryObject<Block> VILLAGE_BRAIN_NODE = BLOCKS.register(
            "village_brain_node",
            () -> new VillageBrainNodeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(50.0F, 1200.0F)              // obsidian toughness
                    .sound(SoundType.STONE)
                    // Glows at least as bright as a fertile block (FERTILE_LIGHT = 5), brighter as it levels up.
                    .lightLevel(s -> 15)));   // same full light as the Forgotten Glowstone block

    public static final RegistryObject<Item> VILLAGE_BRAIN_NODE_ITEM = ITEMS.register(
            "village_brain_node",
            () -> new BlockItem(VILLAGE_BRAIN_NODE.get(), new Item.Properties()));

    /** A throwaway scaffold villagers pillar with -- auto-breaks after ~1 minute (see {@link CheapScaffoldBlock}). */
    public static final RegistryObject<Block> CHEAP_SCAFFOLD = BLOCKS.register(
            "cheap_scaffold",
            () -> new CheapScaffoldBlock(BlockBehaviour.Properties.copy(Blocks.SCAFFOLDING)));

    public static final RegistryObject<Item> CHEAP_SCAFFOLD_ITEM = ITEMS.register(
            "cheap_scaffold",
            () -> new ScaffoldingBlockItem(CHEAP_SCAFFOLD.get(), new Item.Properties()));

    /**
     * "Fake cobblestone": a mossy-cobble look-alike smart door -- allies (players/villagers/golems/
     * guards) phase through it while everyone else hits solid cobblestone. Emits light level 7. The
     * isSuffocating/isViewBlocking predicates are false so an ally inside it takes no damage / no
     * black overlay (these are Properties predicates, not block-method overrides).
     */
    public static final RegistryObject<Block> FAKE_COBBLESTONE = BLOCKS.register(
            "fake_cobblestone",
            () -> new FakeCobblestoneBlock(BlockBehaviour.Properties.copy(Blocks.MOSSY_COBBLESTONE)
                    .lightLevel(s -> 7)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> FAKE_COBBLESTONE_ITEM = ITEMS.register(
            "fake_cobblestone",
            () -> new TooltipBlockItem(FAKE_COBBLESTONE.get(), new Item.Properties(),
                    "block.thevillagefightsback.fake_cobblestone.desc"));

    /** "Fake obsidian": the stage-3 OUTER wall's gate -- same smart-door phasing as fake cobblestone, but with
     *  obsidian toughness so attackers can't break the gate. */
    public static final RegistryObject<Block> FAKE_OBSIDIAN = BLOCKS.register(
            "fake_obsidian",
            () -> new FakeObsidianBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                    .lightLevel(s -> 7)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> FAKE_OBSIDIAN_ITEM = ITEMS.register(
            "fake_obsidian",
            () -> new TooltipBlockItem(FAKE_OBSIDIAN.get(), new Item.Properties(),
                    "block.thevillagefightsback.fake_obsidian.desc"));

    // --- "Nodes": glowing ancient-debris-tough ore blocks. All add +10% to the village's daily sunset harvest
    //     (within a brain node's range); each also spawns its own reinforcement every sunset + on creation. ---
    private static BlockBehaviour.Properties nodeProps() {
        // pushReaction(BLOCK): pistons can't shove a node around (which would otherwise re-fire its spawn).
        return BlockBehaviour.Properties.copy(Blocks.ANCIENT_DEBRIS).lightLevel(s -> 13)
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    /** Golem Factory Node (formerly the Emerald Node): green, growing circle; spawns an iron/gigachad golem. */
    public static final RegistryObject<Block> GOLEM_FACTORY_NODE = BLOCKS.register(
            "golem_factory_node", () -> new GolemFactoryNodeBlock(nodeProps()));
    public static final RegistryObject<Item> GOLEM_FACTORY_NODE_ITEM = ITEMS.register(
            "golem_factory_node", () -> new TooltipBlockItem(GOLEM_FACTORY_NODE.get(), new Item.Properties(),
                    "block.thevillagefightsback.golem_factory_node.desc"));

    /** Barracks Node: orange, growing square; spawns 4 stage-appropriate guards (1/sec). */
    public static final RegistryObject<Block> BARRACKS_NODE = BLOCKS.register(
            "barracks_node", () -> new BarracksNodeBlock(nodeProps()));
    public static final RegistryObject<Item> BARRACKS_NODE_ITEM = ITEMS.register(
            "barracks_node", () -> new TooltipBlockItem(BARRACKS_NODE.get(), new Item.Properties(),
                    "block.thevillagefightsback.barracks_node.desc"));

    /** Lure Tower Node: purple, growing triangle; spawns a wall defender ABOVE it (no stacking). */
    public static final RegistryObject<Block> LURE_TOWER_NODE = BLOCKS.register(
            "lure_tower_node", () -> new LureTowerNodeBlock(nodeProps()));
    public static final RegistryObject<Item> LURE_TOWER_NODE_ITEM = ITEMS.register(
            "lure_tower_node", () -> new TooltipBlockItem(LURE_TOWER_NODE.get(), new Item.Properties(),
                    "block.thevillagefightsback.lure_tower_node.desc"));

    /** Debris: a colour-inverted ("negative") ancient debris with the same toughness; smelting/blasting it
     *  yields an iron ingot. */
    public static final RegistryObject<Block> DEBRIS = BLOCKS.register(
            "debris",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.ANCIENT_DEBRIS)));

    public static final RegistryObject<Item> DEBRIS_ITEM = ITEMS.register(
            "debris",
            () -> new TooltipBlockItem(DEBRIS.get(), new Item.Properties(),
                    "block.thevillagefightsback.debris.desc"));

    /** Obsidian stairs: the stage-3 OUTER wall's loophole stairs, so the obsidian curtain isn't broken up by
     *  cobblestone stairs. Same obsidian toughness (needs a diamond pickaxe -- see the block tags). */
    public static final RegistryObject<Block> OBSIDIAN_STAIRS = BLOCKS.register(
            "obsidian_stairs",
            () -> new StairBlock(() -> Blocks.OBSIDIAN.defaultBlockState(),
                    BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)));

    public static final RegistryObject<Item> OBSIDIAN_STAIRS_ITEM = ITEMS.register(
            "obsidian_stairs",
            () -> new BlockItem(OBSIDIAN_STAIRS.get(), new Item.Properties()));

    /** A purely decorative Iron Golem Head: an iron-block-tough bust shaped like the golem's head (rendered
     *  from the vanilla iron golem texture). Faces the placer; no behaviour. See {@link IronGolemHeadBlock}. */
    public static final RegistryObject<Block> IRON_GOLEM_HEAD = BLOCKS.register(
            "iron_golem_head",
            () -> new IronGolemHeadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> IRON_GOLEM_HEAD_ITEM = ITEMS.register(
            "iron_golem_head",
            () -> new TooltipBlockItem(IRON_GOLEM_HEAD.get(), new Item.Properties(),
                    "block.thevillagefightsback.iron_golem_head.desc"));

    /** Blue TNT: a recolored TNT variant (red -> blue). Behaviour TBD; see {@link BlueTntBlock}. */
    public static final RegistryObject<Block> BLUE_TNT = BLOCKS.register(
            "blue_tnt",
            () -> new BlueTntBlock(BlockBehaviour.Properties.copy(Blocks.TNT)));

    public static final RegistryObject<Item> BLUE_TNT_ITEM = ITEMS.register(
            "blue_tnt",
            () -> new BlockItem(BLUE_TNT.get(), new Item.Properties()));

    /** Green TNT: builds the big square "village tower" structure block-by-block (see {@link GreenTntBlock}). */
    public static final RegistryObject<Block> GREEN_TNT = BLOCKS.register(
            "green_tnt",
            () -> new GreenTntBlock(BlockBehaviour.Properties.copy(Blocks.TNT)));

    public static final RegistryObject<Item> GREEN_TNT_ITEM = ITEMS.register(
            "green_tnt",
            () -> new BlockItem(GREEN_TNT.get(), new Item.Properties()));

    // --- Node-spawning TNTs: each detonates into one node structure (.nbt templates supplied separately).
    //     Colour -> node: yellow = golem factory, orange = barracks, purple = lure tower. ---
    private static net.minecraft.resources.ResourceLocation struct(String name) {
        return new net.minecraft.resources.ResourceLocation(TheVillageFightsBack.MODID, name);
    }

    public static final RegistryObject<Block> YELLOW_TNT = BLOCKS.register(
            "yellow_tnt",
            () -> new StructureTntBlock(BlockBehaviour.Properties.copy(Blocks.TNT), struct("iron_golem_factory")));
    public static final RegistryObject<Item> YELLOW_TNT_ITEM = ITEMS.register(
            "yellow_tnt", () -> new BlockItem(YELLOW_TNT.get(), new Item.Properties()));

    public static final RegistryObject<Block> ORANGE_TNT = BLOCKS.register(
            "orange_tnt",
            () -> new StructureTntBlock(BlockBehaviour.Properties.copy(Blocks.TNT), struct("barracks"),
                    struct("chests/barracks"))); // the barracks chest rolls a weapons-oriented loot table
    public static final RegistryObject<Item> ORANGE_TNT_ITEM = ITEMS.register(
            "orange_tnt", () -> new BlockItem(ORANGE_TNT.get(), new Item.Properties()));

    public static final RegistryObject<Block> PURPLE_TNT = BLOCKS.register(
            "purple_tnt",
            // floorLayer -1: a deep trap -> anchor on the powder-snow surface, which lands one block below the
            // TNT while the lava pit extends downward (and the small tower rises above).
            () -> new StructureTntBlock(BlockBehaviour.Properties.copy(Blocks.TNT), struct("lure_tower_node"), null, -1));
    public static final RegistryObject<Item> PURPLE_TNT_ITEM = ITEMS.register(
            "purple_tnt", () -> new BlockItem(PURPLE_TNT.get(), new Item.Properties()));

    /**
     * Reinforced glass: looks like green tinted glass but is as tough as ancient debris -- hardness 30,
     * blast resistance 1200, and needs a diamond+ pickaxe to break (see the mineable/pickaxe +
     * needs_diamond_tool tags). {@link ReinforcedGlassBlock} makes it fully light-transparent so a dome
     * never darkens the village.
     */
    public static final RegistryObject<Block> REINFORCED_GLASS = BLOCKS.register(
            "reinforced_glass",
            () -> new ReinforcedGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(30.0F, 1200.0F)              // ancient debris toughness
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> REINFORCED_GLASS_ITEM = ITEMS.register(
            "reinforced_glass",
            () -> new BlockItem(REINFORCED_GLASS.get(), new Item.Properties()));

    /**
     * Protecting glass: a directional, one-way-for-projectiles glass (see {@link ProtectingGlassBlock} /
     * {@link ProtectingGlassEvents}). Light-blue tinted look; the white FACING face is the side a projectile
     * can exit. As fragile as normal glass (the one-way projectile block is its only strength) -- it is NOT
     * obsidian-tough, so attackers can break the defender shells open.
     */
    public static final RegistryObject<Block> PROTECTING_GLASS = BLOCKS.register(
            "protecting_glass",
            () -> new ProtectingGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.3F)                 // normal-glass toughness
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> PROTECTING_GLASS_ITEM = ITEMS.register(
            "protecting_glass",
            () -> new BlockItem(PROTECTING_GLASS.get(), new Item.Properties()));

    /**
     * Wall defender spawn egg: places the {@link WallDefenderEntity} conduit-turret creature (100 HP, fires a
     * real JEG bullet/second at the nearest hostile). The BrainNode also spawns one above each gate at level 2.
     */
    public static final RegistryObject<Item> WALL_DEFENDER_SPAWN = ITEMS.register(
            "wall_defender",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.WALL_DEFENDER,
                    0x1b4a4a, 0x78ebf5, new Item.Properties()));

    /** Stage-4 (rocket) wall defender spawn egg -- always in rocket mode + the gold stage-4 texture. */
    public static final RegistryObject<Item> WALL_DEFENDER_STAGE4_SPAWN = ITEMS.register(
            "wall_defender_stage4",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.WALL_DEFENDER_STAGE4,
                    0x6e3a10, 0xffb020, new Item.Properties()));

    /** Defender Golem spawn egg: places the stage-2 evolution of the iron golem (iron body, wall-defender
     *  head in a protecting-glass cocoon, 150 HP, melee + turret). See {@link DefenderGolemEntity}. */
    public static final RegistryObject<Item> DEFENDER_GOLEM_SPAWN = ITEMS.register(
            "defender_golem",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.DEFENDER_GOLEM,
                    0xDBD3D0, 0x78ebf5, new Item.Properties()));

    /** Spawn egg for the {@link BrainNodeWardenEntity} (a green Warden with a brainNode-block head). */
    public static final RegistryObject<Item> BRAIN_NODE_WARDEN_SPAWN = ITEMS.register(
            "brain_node_warden",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.UNLEASHED_BRAIN_NODE,
                    0x16331a, 0x55e07a, new Item.Properties()));

    // --- Fertile / Rich block family --------------------------------------------------------------
    // For each base block, "Fertile X" is a green-tinted copy with the SAME properties as the base, and
    // "Rich X" is the same with emerald gems -- it drops emeralds like emerald ore (via its loot table) but
    // keeps the base's toughness/tool requirement. Sand & gravel variants fall (FallingBlock); logs are
    // axis-aware (RotatedPillarBlock). Textures/models/blockstates/loot are data-generated under resources.
    public static final java.util.List<RegistryObject<Item>> FERTILE_RICH_ITEMS = new java.util.ArrayList<>();

    private enum FertileKind { CUBE, FALLING, LOG, SCULK, LEAVES }

    static {
        registerFertileRich("dirt", Blocks.DIRT, FertileKind.CUBE);
        registerFertileRich("stone", Blocks.STONE, FertileKind.CUBE);
        registerFertileRich("netherrack", Blocks.NETHERRACK, FertileKind.CUBE);
        registerFertileRich("end_stone", Blocks.END_STONE, FertileKind.CUBE);
        registerFertileRich("sculk", Blocks.SCULK, FertileKind.SCULK); // emissive glow like vanilla sculk
        registerFertileRich("sand", Blocks.SAND, FertileKind.FALLING);
        registerFertileRich("gravel", Blocks.GRAVEL, FertileKind.FALLING);
        registerFertileRich("log", Blocks.OAK_LOG, FertileKind.LOG); // "log" = any log; oak is the basis
        registerFertileRich("deepslate", Blocks.DEEPSLATE, FertileKind.LOG); // axis pillar like logs
        registerFertileRich("leaves", Blocks.OAK_LEAVES, FertileKind.LEAVES); // any leaves; oak is the basis
        registerFertileRich("blackstone", Blocks.BLACKSTONE, FertileKind.CUBE);
        registerFertileRich("basalt", Blocks.BASALT, FertileKind.LOG);          // axis pillar
        registerFertileRich("soul_sand", Blocks.SOUL_SAND, FertileKind.CUBE);
        registerFertileRich("soul_soil", Blocks.SOUL_SOIL, FertileKind.CUBE);
        registerFertileRich("snow_block", Blocks.SNOW_BLOCK, FertileKind.CUBE);
    }

    private static void registerFertileRich(String base, Block vanilla, FertileKind kind) {
        registerFertileBlock("fertile_" + base, vanilla, kind);
        registerFertileBlock("rich_" + base, vanilla, kind);
    }

    /** Weak light every fertile/rich block emits, so a fertile zone faintly glows green into its surroundings. */
    public static final int FERTILE_LIGHT = 5;

    private static void registerFertileBlock(String name, Block vanilla, FertileKind kind) {
        // if/else, not switch-over-enum, to avoid generating a synthetic switch-map class.
        RegistryObject<Block> block = BLOCKS.register(name, () -> {
            BlockBehaviour.Properties p = BlockBehaviour.Properties.copy(vanilla);
            // Every fertile/rich block renders FULLBRIGHT (so the green specks glow) AND emits a weak light,
            // so a fertile zone glows green and softly lights its surroundings at night.
            p.emissiveRendering((s, l, pos) -> true).lightLevel(s -> FERTILE_LIGHT);
            if (kind == FertileKind.FALLING) return new FallingBlock(p);
            if (kind == FertileKind.LOG) return new RotatedPillarBlock(p);
            if (kind == FertileKind.LEAVES) {
                // A plain (non-LeavesBlock) block so infected leaves NEVER decay; copy() carries leaves'
                // non-occlusion. The model is cutout_mipped so the leaf gaps show.
                return new Block(p.noOcclusion());
            }
            return new Block(p);
        });
        FERTILE_RICH_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    // Shape-preserving fertile blocks: an infected stair/slab/fence becomes one of these (same shape) so the
    // structure keeps its geometry instead of being filled with a solid fertile cube. The base material is
    // arbitrary -- only the SHAPE matters; they reuse the fertile_stone texture.
    public static final RegistryObject<Block> FERTILE_STAIRS = BLOCKS.register("fertile_stairs",
            () -> new StairBlock(() -> Blocks.STONE.defaultBlockState(), BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<Block> FERTILE_SLAB = BLOCKS.register("fertile_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<Block> FERTILE_FENCE = BLOCKS.register("fertile_fence",
            () -> new FenceBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<Item> FERTILE_STAIRS_ITEM = ITEMS.register("fertile_stairs",
            () -> new BlockItem(FERTILE_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> FERTILE_SLAB_ITEM = ITEMS.register("fertile_slab",
            () -> new BlockItem(FERTILE_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> FERTILE_FENCE_ITEM = ITEMS.register("fertile_fence",
            () -> new BlockItem(FERTILE_FENCE.get(), new Item.Properties()));
    /** Fertile iron bars: shape-preserving conversion of vanilla iron bars. No rich variant. */
    public static final RegistryObject<Block> FERTILE_IRON_BARS = BLOCKS.register("fertile_iron_bars",
            () -> new net.minecraft.world.level.block.IronBarsBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BARS)
                    .emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Item> FERTILE_IRON_BARS_ITEM = ITEMS.register("fertile_iron_bars",
            () -> new BlockItem(FERTILE_IRON_BARS.get(), new Item.Properties()));

    /** Fertile Grass: the green glowing PLANT (not a block). Any plant the infection reaches becomes this.
     *  Emissive (vivid green); no rich variant. See {@link FertileGrassBlock} + FertileFlora. */
    public static final RegistryObject<Block> FERTILE_GRASS = BLOCKS.register("fertile_grass",
            () -> new FertileGrassBlock(BlockBehaviour.Properties.copy(Blocks.GRASS).emissiveRendering((s, l, p) -> true)));
    public static final RegistryObject<Item> FERTILE_GRASS_ITEM = ITEMS.register("fertile_grass",
            () -> new BlockItem(FERTILE_GRASS.get(), new Item.Properties()));

    // Craftable fertile wood + cobble: fertile_planks (from fertile logs) -> stairs/slabs; fertile_cobblestone
    // (dropped only by mining fertile stone). They work in vanilla recipes via item tags (planks / stone tool
    // materials) -- no tint on the crafted output.
    public static final RegistryObject<Block> FERTILE_PLANKS = BLOCKS.register("fertile_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Block> FERTILE_PLANKS_STAIRS = BLOCKS.register("fertile_planks_stairs",
            () -> new StairBlock(() -> FERTILE_PLANKS.get().defaultBlockState(), BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Block> FERTILE_PLANKS_SLAB = BLOCKS.register("fertile_planks_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Block> FERTILE_COBBLESTONE = BLOCKS.register("fertile_cobblestone",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COBBLESTONE).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Item> FERTILE_PLANKS_ITEM = ITEMS.register("fertile_planks",
            () -> new BlockItem(FERTILE_PLANKS.get(), new Item.Properties()));
    public static final RegistryObject<Item> FERTILE_PLANKS_STAIRS_ITEM = ITEMS.register("fertile_planks_stairs",
            () -> new BlockItem(FERTILE_PLANKS_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> FERTILE_PLANKS_SLAB_ITEM = ITEMS.register("fertile_planks_slab",
            () -> new BlockItem(FERTILE_PLANKS_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> FERTILE_COBBLESTONE_ITEM = ITEMS.register("fertile_cobblestone",
            () -> new BlockItem(FERTILE_COBBLESTONE.get(), new Item.Properties()));

    // Fertile / Rich chorus: the fertile chorus FLOWER grows like a vanilla chorus flower but spreads OUR
    // fertile chorus (plant + flower). The RICH variants are decorative / harvest-only -- they never grow and
    // drop an emerald. The plant blocks are ChorusPlantBlock subclasses so they keep the chorus model/shape.
    // Both fertile and rich chorus are full chorus implementations (flower grows + spreads its plant, plant
    // connects/survives) -- they differ only in texture + drops (rich drops emeralds). randomTicks() so the
    // flower actually grows; the family suppliers wire each flower to spread its own plant.
    public static final RegistryObject<Block> FERTILE_CHORUS_PLANT = BLOCKS.register("fertile_chorus_plant",
            () -> new FertileChorusPlantBlock(BlockBehaviour.Properties.copy(Blocks.CHORUS_PLANT).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT), () -> ModBlocks.FERTILE_CHORUS_FLOWER.get()));
    public static final RegistryObject<Block> FERTILE_CHORUS_FLOWER = BLOCKS.register("fertile_chorus_flower",
            () -> new FertileChorusFlowerBlock(BlockBehaviour.Properties.copy(Blocks.CHORUS_FLOWER).randomTicks().emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT), () -> ModBlocks.FERTILE_CHORUS_PLANT.get()));
    public static final RegistryObject<Block> RICH_CHORUS_PLANT = BLOCKS.register("rich_chorus_plant",
            () -> new FertileChorusPlantBlock(BlockBehaviour.Properties.copy(Blocks.CHORUS_PLANT).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT), () -> ModBlocks.RICH_CHORUS_FLOWER.get()));
    public static final RegistryObject<Block> RICH_CHORUS_FLOWER = BLOCKS.register("rich_chorus_flower",
            () -> new FertileChorusFlowerBlock(BlockBehaviour.Properties.copy(Blocks.CHORUS_FLOWER).randomTicks().emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT), () -> ModBlocks.RICH_CHORUS_PLANT.get()));
    public static final RegistryObject<Item> FERTILE_CHORUS_PLANT_ITEM = ITEMS.register("fertile_chorus_plant",
            () -> new BlockItem(FERTILE_CHORUS_PLANT.get(), new Item.Properties()));
    public static final RegistryObject<Item> FERTILE_CHORUS_FLOWER_ITEM = ITEMS.register("fertile_chorus_flower",
            () -> new BlockItem(FERTILE_CHORUS_FLOWER.get(), new Item.Properties()));
    public static final RegistryObject<Item> RICH_CHORUS_PLANT_ITEM = ITEMS.register("rich_chorus_plant",
            () -> new BlockItem(RICH_CHORUS_PLANT.get(), new Item.Properties()));
    public static final RegistryObject<Item> RICH_CHORUS_FLOWER_ITEM = ITEMS.register("rich_chorus_flower",
            () -> new BlockItem(RICH_CHORUS_FLOWER.get(), new Item.Properties()));

    // Fertile / Rich sculk catalyst: on a nearby mob death it spreads fertile / rich sculk like vanilla
    // sculk spreads from a catalyst (see SculkCatalystEvents). Glows like the sculk family.
    public static final RegistryObject<Block> FERTILE_SCULK_CATALYST = BLOCKS.register("fertile_sculk_catalyst",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SCULK_CATALYST).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Block> RICH_SCULK_CATALYST = BLOCKS.register("rich_sculk_catalyst",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SCULK_CATALYST).emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Item> FERTILE_SCULK_CATALYST_ITEM = ITEMS.register("fertile_sculk_catalyst",
            () -> new BlockItem(FERTILE_SCULK_CATALYST.get(), new Item.Properties()));
    public static final RegistryObject<Item> RICH_SCULK_CATALYST_ITEM = ITEMS.register("rich_sculk_catalyst",
            () -> new BlockItem(RICH_SCULK_CATALYST.get(), new Item.Properties()));

    /** Fertile door: the infection replaces every (wooden) door with this. Oak-door shape, green-tinted. */
    public static final RegistryObject<Block> FERTILE_DOOR = BLOCKS.register("fertile_door",
            () -> new net.minecraft.world.level.block.DoorBlock(
                    BlockBehaviour.Properties.copy(Blocks.OAK_DOOR).noOcclusion().emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT),
                    net.minecraft.world.level.block.state.properties.BlockSetType.OAK));
    public static final RegistryObject<Item> FERTILE_DOOR_ITEM = ITEMS.register("fertile_door",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(FERTILE_DOOR.get(), new Item.Properties()));

    /**
     * Limbo Bed: an indestructible (bedrock-tough), bedrock-textured "bed" that sits at a random spot inside
     * the Limbo cube. Sleeping on it (right-click) sends the entity back to where it came from -- see
     * {@link LimboBedBlock}.
     */
    public static final RegistryObject<Block> LIMBO_BED = BLOCKS.register("limbo_bed",
            () -> new LimboBedBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F)     // unbreakable, bedrock blast resistance
                    .sound(SoundType.STONE)
                    .noOcclusion()                   // low bed-height block
                    .noLootTable()));
    public static final RegistryObject<Item> LIMBO_BED_ITEM = ITEMS.register("limbo_bed",
            () -> new BlockItem(LIMBO_BED.get(), new Item.Properties()));

    /**
     * Limbo Gravel: a darker gravel that marks where a creature stood before being pulled into Limbo. If it
     * is destroyed, that creature returns to this exact spot ({@link LimboGravelBlock}). Soul particles rise
     * when something walks on it.
     */
    public static final RegistryObject<Block> LIMBO_GRAVEL = BLOCKS.register("limbo_gravel",
            () -> new LimboGravelBlock(BlockBehaviour.Properties.copy(Blocks.GRAVEL)
                    .mapColor(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Item> LIMBO_GRAVEL_ITEM = ITEMS.register("limbo_gravel",
            () -> new BlockItem(LIMBO_GRAVEL.get(), new Item.Properties()));

    /**
     * Limbo Egg: a black-and-white dragon egg that nests in the Limbo. It does NOT teleport when clicked;
     * destroying it (other than with Silk Touch) hatches a Limbo Phantom ({@link LimboEggBlock}).
     */
    public static final RegistryObject<Block> LIMBO_EGG = BLOCKS.register("limbo_egg",
            () -> new LimboEggBlock(BlockBehaviour.Properties.copy(Blocks.DRAGON_EGG)
                    .mapColor(MapColor.COLOR_BLACK)
                    .randomTicks()));        // enables the small natural-hatch chance
    public static final RegistryObject<Item> LIMBO_EGG_ITEM = ITEMS.register("limbo_egg",
            () -> new TooltipBlockItem(LIMBO_EGG.get(), new Item.Properties(),
                    "block.thevillagefightsback.limbo_egg.desc"));

    /**
     * Forgotten Glowstone: a vivid green glowstone that sparsely encrusts Limbo's bedrock cubes (placed like
     * moss). It glows at the engine maximum (light 15 -- "twice glowstone" is capped there) and is as tough as
     * obsidian (needs a diamond+ pickaxe). Drops Forgotten Powder unless mined with Silk Touch (loot table).
     */
    public static final RegistryObject<Block> FORGOTTEN_GLOWSTONE = BLOCKS.register("forgotten_glowstone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(50.0F, 1200.0F)              // obsidian toughness
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .lightLevel(s -> 15)                   // max light (the engine cap)
                    .isValidSpawn((s, l, p, e) -> false)));
    public static final RegistryObject<Item> FORGOTTEN_GLOWSTONE_ITEM = ITEMS.register("forgotten_glowstone",
            () -> new TooltipBlockItem(FORGOTTEN_GLOWSTONE.get(), new Item.Properties(),
                    "block.thevillagefightsback.forgotten_glowstone.desc"));

    /** "Fake bedrock": looks/breaks exactly like bedrock (unbreakable except by the Limbo Pickaxe) but every
     *  creature passes through it -- the whole Limbo is built from it (see {@link FakeBedrockBlock}). The
     *  isSuffocating/isViewBlocking predicates are false so a creature inside it is unharmed + unobscured. */
    public static final RegistryObject<Block> FAKE_BEDROCK = BLOCKS.register("fake_bedrock",
            () -> new FakeBedrockBlock(BlockBehaviour.Properties.copy(Blocks.BEDROCK)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Item> FAKE_BEDROCK_ITEM = ITEMS.register("fake_bedrock",
            () -> new TooltipBlockItem(FAKE_BEDROCK.get(), new Item.Properties(),
                    "block.thevillagefightsback.fake_bedrock.desc"));

    /**
     * Limbo Block: a storage block crafted from 9 Limbo Ingots. As tough as bedrock against explosions (blast
     * resistance 1200) yet still obtainable -- a diamond+ pickaxe mines it back (hardness 50, like obsidian/the
     * brain node). Works as a beacon base (see the beacon_base_blocks tag). Carries the Limbo gear's animated
     * green shimmer (animated texture + .mcmeta).
     */
    public static final RegistryObject<Block> LIMBO_BLOCK = BLOCKS.register("limbo_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(50.0F, 1200.0F)              // obsidian/bedrock-tough vs explosions, still mineable
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));
    // Plain BlockItem: ModTooltips auto-appends "<descId>.desc" (we must NOT override appendHoverText). Fire-
    // resistant like the rest of the Limbo gear it is crafted from.
    public static final RegistryObject<Item> LIMBO_BLOCK_ITEM = ITEMS.register("limbo_block",
            () -> new BlockItem(LIMBO_BLOCK.get(), new Item.Properties().fireResistant()));

    /**
     * The Limbo Portal: a green nether-portal that forms inside a lit {@link #FAKE_OBSIDIAN} frame and
     * teleports between the overworld and Limbo at 16x compression ({@link LimboPortalBlock} /
     * {@link LimboPortalForcer}). No item form (like the nether portal); unbreakable; glows.
     */
    public static final RegistryObject<Block> LIMBO_PORTAL = BLOCKS.register("limbo_portal",
            () -> new LimboPortalBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(s -> 11)
                    .noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    /** The mod's own creative tab. New blocks/items get added here over time. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + TheVillageFightsBack.MODID))
            .icon(() -> new ItemStack(VILLAGE_BRAIN_NODE_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(VILLAGE_BRAIN_NODE_ITEM.get());
                output.accept(CHEAP_SCAFFOLD_ITEM.get());
                output.accept(FAKE_COBBLESTONE_ITEM.get());
                output.accept(FAKE_OBSIDIAN_ITEM.get());
                output.accept(GOLEM_FACTORY_NODE_ITEM.get());
                output.accept(BARRACKS_NODE_ITEM.get());
                output.accept(LURE_TOWER_NODE_ITEM.get());
                output.accept(DEBRIS_ITEM.get());
                output.accept(OBSIDIAN_STAIRS_ITEM.get());
                output.accept(IRON_GOLEM_HEAD_ITEM.get());
                output.accept(BLUE_TNT_ITEM.get());
                output.accept(GREEN_TNT_ITEM.get());
                output.accept(YELLOW_TNT_ITEM.get());
                output.accept(ORANGE_TNT_ITEM.get());
                output.accept(PURPLE_TNT_ITEM.get());
                output.accept(REINFORCED_GLASS_ITEM.get());
                output.accept(PROTECTING_GLASS_ITEM.get());
                output.accept(WALL_DEFENDER_SPAWN.get());
                output.accept(WALL_DEFENDER_STAGE4_SPAWN.get());
                output.accept(DEFENDER_GOLEM_SPAWN.get());
                output.accept(BRAIN_NODE_WARDEN_SPAWN.get());
                output.accept(ModItems.LIMBO_PHANTOM_SPAWN.get());
                output.accept(ModItems.EMERALDGER_SPAWN.get());
                output.accept(ModItems.RICH_SHEEP_SPAWN.get());
                output.accept(ModItems.VINDICTIVE_GUARD_SPAWN.get());
                output.accept(ModItems.VINDICTIVE_GIGACHAD_SPAWN.get());
                for (RegistryObject<Item> egg : ModItems.LIMBO_RAID_EGGS) output.accept(egg.get());
                for (RegistryObject<Item> egg : ModItems.STAGED_EGGS) output.accept(egg.get());
                output.accept(ModItems.ALIEN_SLASHER.get());
                output.accept(ModItems.ALIEN_ERADICATOR.get());
                output.accept(ModItems.ROCKET_LAUNCHER.get());
                output.accept(ModItems.ROCKET.get());
                output.accept(ModItems.FIRE_GRENADE.get());
                output.accept(ModItems.MACE.get());
                // Alien Smasher, shown pre-enchanted (Density 3 + Wind Burst 3 + Fire Aspect 2) like a guard mace.
                ItemStack smasher = new ItemStack(ModItems.ALIEN_SMASHER.get());
                smasher.enchant(ModEnchantments.DENSITY.get(), 3);
                smasher.enchant(ModEnchantments.WIND_BURST.get(), 3);
                smasher.enchant(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT, 2);
                output.accept(smasher);
                output.accept(ModItems.WIND_CHARGE.get());
                // The three mace enchanted books (at max level): native, not craftable.
                output.accept(net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                        new net.minecraft.world.item.enchantment.EnchantmentInstance(ModEnchantments.DENSITY.get(), 5)));
                output.accept(net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                        new net.minecraft.world.item.enchantment.EnchantmentInstance(ModEnchantments.BREACH.get(), 4)));
                output.accept(net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                        new net.minecraft.world.item.enchantment.EnchantmentInstance(ModEnchantments.WIND_BURST.get(), 3)));
                // Emerald gear (built from emeralds; stats between iron and diamond) -- in OUR tab.
                output.accept(ModArmor.EMERALD_HELMET.get());
                output.accept(ModArmor.EMERALD_CHESTPLATE.get());
                output.accept(ModArmor.EMERALD_LEGGINGS.get());
                output.accept(ModArmor.EMERALD_BOOTS.get());
                for (RegistryObject<Item> tool : ModItems.EMERALD_TOOLS) output.accept(tool.get());
                output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(net.minecraft.world.item.Items.POTION), ModPotions.IMMUNITY.get())); // Potion of Immunity
                output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(net.minecraft.world.item.Items.POTION), ModPotions.KARATS_WILL.get())); // Potion of Karat's Will
                output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(net.minecraft.world.item.Items.POTION), ModPotions.KARATS_WILL_LONG.get())); // Karat's Will (long)
                output.accept(ModItems.EMERALD_APPLE.get());
                output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(net.minecraft.world.item.Items.POTION), ModPotions.EMERALD_VIRUS.get())); // Potion of Emerald Virus
                for (RegistryObject<Item> it : FERTILE_RICH_ITEMS) output.accept(it.get());
                output.accept(FERTILE_STAIRS_ITEM.get());
                output.accept(FERTILE_SLAB_ITEM.get());
                output.accept(FERTILE_FENCE_ITEM.get());
                output.accept(FERTILE_IRON_BARS_ITEM.get());
                output.accept(FERTILE_GRASS_ITEM.get());
                output.accept(FERTILE_PLANKS_ITEM.get());
                output.accept(FERTILE_PLANKS_STAIRS_ITEM.get());
                output.accept(FERTILE_PLANKS_SLAB_ITEM.get());
                output.accept(FERTILE_COBBLESTONE_ITEM.get());
                output.accept(FERTILE_DOOR_ITEM.get());
                output.accept(FERTILE_CHORUS_PLANT_ITEM.get());
                output.accept(FERTILE_CHORUS_FLOWER_ITEM.get());
                output.accept(RICH_CHORUS_PLANT_ITEM.get());
                output.accept(RICH_CHORUS_FLOWER_ITEM.get());
                output.accept(ModItems.FERTILE_CHORUS.get());
                output.accept(FERTILE_SCULK_CATALYST_ITEM.get());
                output.accept(RICH_SCULK_CATALYST_ITEM.get());
                output.accept(LIMBO_BED_ITEM.get());
                output.accept(LIMBO_GRAVEL_ITEM.get());
                output.accept(LIMBO_EGG_ITEM.get());
                output.accept(FORGOTTEN_GLOWSTONE_ITEM.get());
                output.accept(FAKE_BEDROCK_ITEM.get());
                output.accept(ModItems.FORGOTTEN_POWDER.get());
                output.accept(ModItems.LIMBO_POTION.get());
                output.accept(ModItems.VOLATILE_LIMBO_POTION.get());
                output.accept(ModItems.LIMBO_MEMBRANE.get());
                output.accept(ModItems.LIMBO_INGOT.get());
                output.accept(LIMBO_BLOCK_ITEM.get());
                output.accept(ModItems.LIMBO_SMITHING_TEMPLATE.get());
                output.accept(ModArmor.LIMBO_HELMET.get());
                output.accept(ModArmor.LIMBO_CHESTPLATE.get());
                output.accept(ModArmor.LIMBO_ELYTRA_CHESTPLATE.get());
                output.accept(ModArmor.LIMBO_LEGGINGS.get());
                output.accept(ModArmor.LIMBO_BOOTS.get());
                output.accept(ModItems.LIMBO_SWORD.get());
                output.accept(ModItems.LIMBO_PICKAXE.get());
                output.accept(ModItems.LIMBO_AXE.get());
                output.accept(ModItems.LIMBO_SHOVEL.get());
                output.accept(ModItems.LIMBO_HOE.get());
                output.accept(ModItems.EMERALD_VIRUS_ARROW.get());
                output.accept(ModItems.EMERALD_VIRUS_ARROW_2.get());
            })
            .build());

    /** Register the DeferredRegisters on the mod event bus (call from the mod constructor). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
