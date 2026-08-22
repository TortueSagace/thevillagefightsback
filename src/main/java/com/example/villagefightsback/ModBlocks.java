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
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TheVillageFightsBack.MODID);

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
     * guards) phase through it while everyone else hits solid cobblestone. Emits light level 7.
     *
     * <p>The two Properties predicates (not block-method overrides) are set OPPOSITE ways on purpose:
     * <ul>
     *   <li>{@code isSuffocating} false, so standing in the gate never costs an ally any health.</li>
     *   <li>{@code isViewBlocking} TRUE, so an ally standing in it gets vanilla's in-wall overlay: the
     *       cobble texture fills the screen exactly as if they were buried in real stone.</li>
     * </ul>
     * Both used to be false, which let a player park inside the wall and watch the battle through it in
     * perfect comfort -- a one-way window with no cost, which made the gate far too strong. The gate is
     * meant to be walked THROUGH, so passing costs a moment of blindness and camping in it costs sight
     * altogether, while still costing no health.
     */
    public static final RegistryObject<Block> FAKE_COBBLESTONE = BLOCKS.register(
            "fake_cobblestone",
            () -> new FakeCobblestoneBlock(BlockBehaviour.Properties.copy(Blocks.MOSSY_COBBLESTONE)
                    .lightLevel(s -> 7)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> true)));

    public static final RegistryObject<Item> FAKE_COBBLESTONE_ITEM = ITEMS.register(
            "fake_cobblestone",
            () -> new TooltipBlockItem(FAKE_COBBLESTONE.get(), new Item.Properties(),
                    "block.thevillagefightsback.fake_cobblestone.desc"));

    /** "Fake obsidian": the stage-3 OUTER wall's gate -- same smart-door phasing as fake cobblestone, but with
     *  obsidian toughness so attackers can't break the gate. Carries the same harmless-but-blinding pair as
     *  {@link #FAKE_COBBLESTONE}: no suffocation damage, but the in-wall overlay while you are inside it. */
    public static final RegistryObject<Block> FAKE_OBSIDIAN = BLOCKS.register(
            "fake_obsidian",
            () -> new FakeObsidianBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                    .lightLevel(s -> 7)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> true)));

    public static final RegistryObject<Item> FAKE_OBSIDIAN_ITEM = ITEMS.register(
            "fake_obsidian",
            () -> new TooltipBlockItem(FAKE_OBSIDIAN.get(), new Item.Properties(),
                    "block.thevillagefightsback.fake_obsidian.desc"));

    // --- X-ray blocks: the Fake blocks with their yellow growth burned cold blue, and the ONE behavioural
    //     difference that the colour advertises. ---
    //
    // Every X-ray block is its Fake counterpart in all but two respects:
    //   1. isViewBlocking is false, so standing inside one you see straight out, which is exactly how the
    //      Fake blocks behaved before they were made blinding. The old free-window trick is not gone, it is
    //      now something you PAY for: an echo shard and a night vision potion per block.
    //   2. The texture is blue instead of yellow, so an X-ray gate is recognisable from across the village
    //      and nobody has to remember which of two identical-looking walls they can see through.
    //
    // They reuse the Fake block CLASSES outright, since the phasing, pathfinding and floor rules are all
    // meant to be identical -- only the Properties differ. Fake bedrock's sibling picks up light level 7
    // (which fake bedrock lacks) so its veins actually glow like the other two.

    public static final RegistryObject<Block> X_RAY_COBBLESTONE = BLOCKS.register(
            "x_ray_cobblestone",
            () -> new FakeCobblestoneBlock(BlockBehaviour.Properties.copy(Blocks.MOSSY_COBBLESTONE)
                    .lightLevel(s -> 7)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> X_RAY_COBBLESTONE_ITEM = ITEMS.register(
            "x_ray_cobblestone",
            () -> new TooltipBlockItem(X_RAY_COBBLESTONE.get(), new Item.Properties(),
                    "block.thevillagefightsback.x_ray_cobblestone.desc"));

    public static final RegistryObject<Block> X_RAY_OBSIDIAN = BLOCKS.register(
            "x_ray_obsidian",
            () -> new FakeObsidianBlock(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                    .lightLevel(s -> 7)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> X_RAY_OBSIDIAN_ITEM = ITEMS.register(
            "x_ray_obsidian",
            () -> new TooltipBlockItem(X_RAY_OBSIDIAN.get(), new Item.Properties(),
                    "block.thevillagefightsback.x_ray_obsidian.desc"));

    /**
     * The husk a defeated brain node leaves behind. Same toughness as the live node so it is no easier to dig
     * out, but it glows at nothing: the light level is 0 and the animated top is replaced by a black, cracked
     * one. See {@link DeadBrainNodeBlock} for why it is not tracked as a brain node.
     */
    public static final RegistryObject<Block> DEAD_BRAIN_NODE = BLOCKS.register(
            "dead_brain_node",
            () -> new DeadBrainNodeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(50.0F, 1200.0F)              // as tough as the living node
                    .sound(SoundType.STONE)));

    public static final RegistryObject<Item> DEAD_BRAIN_NODE_ITEM = ITEMS.register(
            "dead_brain_node",
            () -> new TooltipBlockItem(DEAD_BRAIN_NODE.get(), new Item.Properties(),
                    "block.thevillagefightsback.dead_brain_node.desc"));

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

    /** Elegant Stand: a slab-shaped quartz-and-gold display pedestal. A creature standing on it is frozen in
     *  place and always turns to face the nearest player (see {@link ElegantStandEvents}). */
    public static final RegistryObject<Block> ELEGANT_STAND = BLOCKS.register(
            "elegant_stand",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.QUARTZ_BLOCK)));

    public static final RegistryObject<Item> ELEGANT_STAND_ITEM = ITEMS.register(
            "elegant_stand",
            () -> new TooltipBlockItem(ELEGANT_STAND.get(), new Item.Properties(),
                    "block.thevillagefightsback.elegant_stand.desc"));

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

    /** Cyan TNT: the stage-2+ house charge -- one of the three grand houses (see {@link CyanTntBlock}). */
    public static final RegistryObject<Block> CYAN_TNT = BLOCKS.register(
            "cyan_tnt",
            () -> new CyanTntBlock(BlockBehaviour.Properties.copy(Blocks.TNT)));

    public static final RegistryObject<Item> CYAN_TNT_ITEM = ITEMS.register(
            "cyan_tnt",
            () -> new BlockItem(CYAN_TNT.get(), new Item.Properties()));

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
     * Black TNT: the Darius statue.
     *
     * <p>The one structure charge that is not a building. It is placed automatically once General Darius
     * has taken twenty lives (see {@code GeneralDariusEntity}), and is in the creative tab so it can be
     * placed by hand as well. See {@code tools/gen_darius_statue.py} for where the statue comes from --
     * it is his own armour textures, voxelised, not a drawing of him.
     */
    public static final RegistryObject<Block> BLACK_TNT = BLOCKS.register(
            "black_tnt",
            () -> new StructureTntBlock(BlockBehaviour.Properties.copy(Blocks.TNT), struct("darius_statue")));
    public static final RegistryObject<Item> BLACK_TNT_ITEM = ITEMS.register(
            "black_tnt", () -> new BlockItem(BLACK_TNT.get(), new Item.Properties()));

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
            () -> new WallDefenderSpawnEggItem(ModEntities.WALL_DEFENDER,
                    0x1b4a4a, 0x78ebf5, new Item.Properties())); // icon = the live spinning defender (see its item model)

    /** Stage-4 (rocket) wall defender spawn egg -- always in rocket mode + the gold stage-4 texture. */
    public static final RegistryObject<Item> WALL_DEFENDER_STAGE4_SPAWN = ITEMS.register(
            "wall_defender_stage4",
            () -> new WallDefenderSpawnEggItem(ModEntities.WALL_DEFENDER_STAGE4,
                    0x6e3a10, 0xffb020, new Item.Properties())); // icon = the live spinning gold stage-4 defender

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
        registerFertileRich("sandstone", Blocks.SANDSTONE, FertileKind.CUBE); // sandstone is solid, it never falls
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
                // A real LeavesBlock (so heightmaps / the wall planner treat it as foliage and render is correct),
                // but one that NEVER decays -- see FertileLeavesBlock. The model is cutout_mipped so the gaps show.
                return new FertileLeavesBlock(p.noOcclusion());
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

    /** Fertile Kelp: the green glowing underwater PLANT. Vanilla kelp the infection reaches becomes this; it
     *  glows like the rest of the fertile family and gives a passing mob the Emerald Virus I. See
     *  {@link FertileKelpBlock}. No rich variant. */
    public static final RegistryObject<Block> FERTILE_KELP = BLOCKS.register("fertile_kelp",
            () -> new FertileKelpBlock(BlockBehaviour.Properties.copy(Blocks.KELP_PLANT)
                    .emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Item> FERTILE_KELP_ITEM = ITEMS.register("fertile_kelp",
            () -> new BlockItem(FERTILE_KELP.get(), new Item.Properties()));

    /** Infested Path (v1.0.3 mob infection): a lime, glowing path laid below an emerald-converted unit. Spreads
     *  fertile/rich blocks like a village path -- to a stage-4 brain radius but 5x slower. See {@link InfestedPathBlock}
     *  + {@link com.example.villagefightsback.infection.InfectionManager}. */
    /** The village's own coloured path, laid along a stage-2 wall footprint. See {@link VillagePathBlock}. */
    public static final RegistryObject<Block> VILLAGE_PATH = BLOCKS.register("village_path",
            () -> new VillagePathBlock(BlockBehaviour.Properties.copy(Blocks.DIRT_PATH)
                    .emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Item> VILLAGE_PATH_ITEM = ITEMS.register("village_path",
            () -> new BlockItem(VILLAGE_PATH.get(), new Item.Properties()));

    public static final RegistryObject<Block> INFESTED_PATH = BLOCKS.register("infested_path",
            () -> new InfestedPathBlock(BlockBehaviour.Properties.copy(Blocks.DIRT_PATH)
                    .emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT)));
    public static final RegistryObject<Item> INFESTED_PATH_ITEM = ITEMS.register("infested_path",
            () -> new BlockItem(INFESTED_PATH.get(), new Item.Properties()));

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

    // --- Colour variants of the whole fertile/rich family ------------------------------------------
    //
    // A wholly green landscape is monotonous, so the same 50 blocks exist in five flavours. Only the tint
    // differs: same classes, same properties, same light, same behaviour. Green keeps the named constants
    // above -- twenty-odd files refer to them -- and the four other colours live in these maps, keyed by
    // registry name, so nothing that already exists has to change.
    //
    // Which colour a converted block comes out as is decided at conversion time from the block being spread
    // FROM (see BlockMatcher.colourOf); there is no per-block colour state and nothing tracks it.

    /** The four non-green sets. Green is the empty suffix and is NOT in {@link #COLOURED}. */
    public static final String[] COLOUR_SUFFIXES = {"_cyan", "_purple", "_orange", "_yellow"};

    /** Every coloured block, by full registry name (e.g. {@code fertile_dirt_cyan}). */
    public static final java.util.Map<String, RegistryObject<Block>> COLOURED = new java.util.LinkedHashMap<>();

    /** Colour suffix -> that set's items, in registration order, for the "VFB: blocks" tab. */
    public static final java.util.Map<String, java.util.List<RegistryObject<Item>>> COLOURED_ITEMS =
            new java.util.LinkedHashMap<>();

    /** The glowing properties every fertile/rich block shares. */
    private static BlockBehaviour.Properties glow(Block vanilla) {
        return BlockBehaviour.Properties.copy(vanilla)
                .emissiveRendering((s, l, p) -> true).lightLevel(s -> FERTILE_LIGHT);
    }

    private static RegistryObject<Block> colour(String sfx, String name, java.util.function.Supplier<Block> factory) {
        RegistryObject<Block> b = BLOCKS.register(name, factory);
        COLOURED.put(name, b);
        COLOURED_ITEMS.computeIfAbsent(sfx, k -> new java.util.ArrayList<>())
                .add(ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties())));
        return b;
    }

    /** Look a coloured block up lazily, for the few that reference a sibling (stairs, chorus). */
    private static Block col(String name) {
        return COLOURED.get(name).get();
    }

    private record ColourBase(String name, Block vanilla, FertileKind kind) {}

    private static final ColourBase[] COLOUR_BASES = {
            new ColourBase("dirt", Blocks.DIRT, FertileKind.CUBE),
            new ColourBase("stone", Blocks.STONE, FertileKind.CUBE),
            new ColourBase("netherrack", Blocks.NETHERRACK, FertileKind.CUBE),
            new ColourBase("end_stone", Blocks.END_STONE, FertileKind.CUBE),
            new ColourBase("sculk", Blocks.SCULK, FertileKind.SCULK),
            new ColourBase("sand", Blocks.SAND, FertileKind.FALLING),
            new ColourBase("sandstone", Blocks.SANDSTONE, FertileKind.CUBE),
            new ColourBase("gravel", Blocks.GRAVEL, FertileKind.FALLING),
            new ColourBase("log", Blocks.OAK_LOG, FertileKind.LOG),
            new ColourBase("deepslate", Blocks.DEEPSLATE, FertileKind.LOG),
            new ColourBase("leaves", Blocks.OAK_LEAVES, FertileKind.LEAVES),
            new ColourBase("blackstone", Blocks.BLACKSTONE, FertileKind.CUBE),
            new ColourBase("basalt", Blocks.BASALT, FertileKind.LOG),
            new ColourBase("soul_sand", Blocks.SOUL_SAND, FertileKind.CUBE),
            new ColourBase("soul_soil", Blocks.SOUL_SOIL, FertileKind.CUBE),
            new ColourBase("snow_block", Blocks.SNOW_BLOCK, FertileKind.CUBE),
    };

    static {
        for (String c : COLOUR_SUFFIXES) {
            // the 32 base blocks
            for (ColourBase cb : COLOUR_BASES) {
                for (String variant : new String[]{"fertile_", "rich_"}) {
                    String name = variant + cb.name() + c;
                    Block vanilla = cb.vanilla();
                    FertileKind kind = cb.kind();
                    colour(c, name, () -> {
                        BlockBehaviour.Properties p = glow(vanilla);
                        if (kind == FertileKind.FALLING) return new FallingBlock(p);
                        if (kind == FertileKind.LOG) return new RotatedPillarBlock(p);
                        if (kind == FertileKind.LEAVES) return new FertileLeavesBlock(p.noOcclusion());
                        return new Block(p);
                    });
                }
            }

            // Shape-preserving stone family. NOTE: no light and no emissive here, exactly as the GREEN ones
            // are registered. The colours must match green block for block rather than be quietly "fixed",
            // otherwise a cyan slab would glow where a green one does not.
            colour(c, "fertile_stairs" + c, () -> new StairBlock(
                    () -> Blocks.STONE.defaultBlockState(), BlockBehaviour.Properties.copy(Blocks.STONE)));
            colour(c, "fertile_slab" + c, () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
            colour(c, "fertile_fence" + c, () -> new FenceBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
            colour(c, "fertile_iron_bars" + c,
                    () -> new net.minecraft.world.level.block.IronBarsBlock(glow(Blocks.IRON_BARS)));
            colour(c, "fertile_grass" + c, () -> new FertileGrassBlock(
                    BlockBehaviour.Properties.copy(Blocks.GRASS).emissiveRendering((s, l, p) -> true)));
            colour(c, "fertile_kelp" + c, () -> new FertileKelpBlock(glow(Blocks.KELP_PLANT)));
            colour(c, "infested_path" + c, () -> new InfestedPathBlock(glow(Blocks.DIRT_PATH)));
            colour(c, "village_path" + c, () -> new VillagePathBlock(glow(Blocks.DIRT_PATH)));

            // craftable wood + cobble
            colour(c, "fertile_planks" + c, () -> new Block(glow(Blocks.OAK_PLANKS)));
            colour(c, "fertile_planks_stairs" + c, () -> new StairBlock(
                    () -> col("fertile_planks" + c).defaultBlockState(), glow(Blocks.OAK_PLANKS)));
            colour(c, "fertile_planks_slab" + c, () -> new SlabBlock(glow(Blocks.OAK_PLANKS)));
            colour(c, "fertile_cobblestone" + c, () -> new Block(glow(Blocks.COBBLESTONE)));

            // chorus + catalyst: each flower spreads its OWN plant, so the pair is wired within the colour
            for (String variant : new String[]{"fertile_", "rich_"}) {
                colour(c, variant + "chorus_plant" + c, () -> new FertileChorusPlantBlock(
                        glow(Blocks.CHORUS_PLANT), () -> col(variant + "chorus_flower" + c)));
                colour(c, variant + "chorus_flower" + c, () -> new FertileChorusFlowerBlock(
                        glow(Blocks.CHORUS_FLOWER).randomTicks(), () -> col(variant + "chorus_plant" + c)));
                colour(c, variant + "sculk_catalyst" + c, () -> new Block(glow(Blocks.SCULK_CATALYST)));
            }

            // the door needs a DoubleHighBlockItem rather than a plain BlockItem, so it is registered by hand
            String doorName = "fertile_door" + c;
            RegistryObject<Block> door = BLOCKS.register(doorName, () -> new net.minecraft.world.level.block.DoorBlock(
                    glow(Blocks.OAK_DOOR).noOcclusion(),
                    net.minecraft.world.level.block.state.properties.BlockSetType.OAK));
            COLOURED.put(doorName, door);
            COLOURED_ITEMS.computeIfAbsent(c, k -> new java.util.ArrayList<>()).add(
                    ITEMS.register(doorName, () -> new net.minecraft.world.item.DoubleHighBlockItem(
                            door.get(), new Item.Properties())));
        }
    }

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
                    .mapColor(MapColor.COLOR_BLACK)
                    .noLootTable()));               // drops are handled in-code (plain item / soul item / restore)
    public static final RegistryObject<Item> LIMBO_GRAVEL_ITEM = ITEMS.register("limbo_gravel",
            () -> new BlockItem(LIMBO_GRAVEL.get(), new Item.Properties()));
    /** The block entity that caches a leashed gravel's render data (held entity type, name, instability deadline). */
    /** The Village Memorial: this mod's own headstone, used to bury a fallen defender when MCA Reborn is not
     *  installed (with MCA the village uses MCA's tombstones instead). Placeable either way. */
    public static final RegistryObject<Block> MEMORIAL = BLOCKS.register("memorial",
            () -> new MemorialBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE_TILES)
                    .mapColor(MapColor.DEEPSLATE)
                    .noOcclusion()));
    public static final RegistryObject<Item> MEMORIAL_ITEM = ITEMS.register("memorial",
            () -> new net.minecraft.world.item.BlockItem(MEMORIAL.get(), new Item.Properties()));

    /** "Do not build here": a player's claim on a patch of ground. See {@link NoBuildSignBlock}. */
    public static final RegistryObject<Block> NO_BUILD_SIGN = BLOCKS.register("no_build_sign",
            () -> new NoBuildSignBlock(BlockBehaviour.Properties.copy(Blocks.OAK_SIGN)
                    .mapColor(MapColor.EMERALD)
                    .noOcclusion()
                    .noCollission()));
    public static final RegistryObject<Item> NO_BUILD_SIGN_ITEM = ITEMS.register("no_build_sign",
            () -> new TooltipBlockItem(NO_BUILD_SIGN.get(), new Item.Properties(),
                    "block.thevillagefightsback.no_build_sign.desc"));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<MemorialBlockEntity>> MEMORIAL_BE =
            BLOCK_ENTITIES.register("memorial", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            MemorialBlockEntity::new, MEMORIAL.get()).build(null));

    /**
     * The Obliteration Furnace: a blast furnace in the Obliterator's violet that burns emeralds and, while
     * lit, keeps creatures out of the world's spawn tables (see {@link ObliterationFurnaceBlock}).
     *
     * <p>Fake-bedrock tough by copying BEDROCK's -1 hardness, so nothing can blow up or mine away a running
     * ban by accident. Like fake bedrock, the Limbo Pickaxe is the way to take it back (see
     * {@link LimboPickaxeEvents}). Full glowstone light while burning, and none at all when cold, which makes
     * a lit furnace visible across a base without anyone opening its screen.
     */
    public static final RegistryObject<Block> OBLITERATION_FURNACE = BLOCKS.register("obliteration_furnace",
            () -> new ObliterationFurnaceBlock(BlockBehaviour.Properties.copy(Blocks.BEDROCK)
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.STONE)
                    .lightLevel(s -> s.getValue(ObliterationFurnaceBlock.LIT) ? 15 : 0)));

    public static final RegistryObject<Item> OBLITERATION_FURNACE_ITEM = ITEMS.register("obliteration_furnace",
            () -> new TooltipBlockItem(OBLITERATION_FURNACE.get(), new Item.Properties(),
                    "block.thevillagefightsback.obliteration_furnace.desc"));

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<ObliterationFurnaceBlockEntity>> OBLITERATION_FURNACE_BE =
            BLOCK_ENTITIES.register("obliteration_furnace", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            ObliterationFurnaceBlockEntity::new, OBLITERATION_FURNACE.get()).build(null));

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<LimboGravelBlockEntity>> LIMBO_GRAVEL_BE =
            BLOCK_ENTITIES.register("limbo_gravel", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            LimboGravelBlockEntity::new, LIMBO_GRAVEL.get()).build(null));
    /** The "soul gravel" carried form: silk-mining an entity's gravel yields this non-stackable, entity-named
     *  item. While it is carried/stored the creature stays in Limbo; it returns when the item is freed. */
    public static final RegistryObject<Item> LIMBO_SOUL_GRAVEL = ITEMS.register("limbo_soul_gravel",
            () -> new LimboSoulGravelItem(new Item.Properties().stacksTo(1)));

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
     *  creature passes through it -- the whole Limbo is built from it (see {@link FakeBedrockBlock}). Carries
     *  the same pair as the other two fake blocks: isSuffocating false so standing in it costs no health, and
     *  isViewBlocking TRUE so it blinds you like the stone it pretends to be. Use {@link #X_RAY_BEDROCK} when
     *  you want to see out. */
    public static final RegistryObject<Block> FAKE_BEDROCK = BLOCKS.register("fake_bedrock",
            () -> new FakeBedrockBlock(BlockBehaviour.Properties.copy(Blocks.BEDROCK)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> true)));
    public static final RegistryObject<Item> FAKE_BEDROCK_ITEM = ITEMS.register("fake_bedrock",
            () -> new TooltipBlockItem(FAKE_BEDROCK.get(), new Item.Properties(),
                    "block.thevillagefightsback.fake_bedrock.desc"));

    /** X-ray bedrock: fake bedrock you can see out of (see the X-ray family note by {@link #X_RAY_COBBLESTONE}).
     *  It takes light level 7 that fake bedrock does not have, so its blue veins read as lit rather than painted,
     *  and it wears its own texture instead of the vanilla one because it is meant to be spotted, not to hide. */
    public static final RegistryObject<Block> X_RAY_BEDROCK = BLOCKS.register("x_ray_bedrock",
            () -> new FakeBedrockBlock(BlockBehaviour.Properties.copy(Blocks.BEDROCK)
                    .lightLevel(s -> 7)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> X_RAY_BEDROCK_ITEM = ITEMS.register("x_ray_bedrock",
            () -> new TooltipBlockItem(X_RAY_BEDROCK.get(), new Item.Properties(),
                    "block.thevillagefightsback.x_ray_bedrock.desc"));

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

    /**
     * One pane of a CHUNK PORTAL (work in progress, v1.2.1): the four walls around a transformed overworld
     * chunk are built from these ({@link ChunkPortalBlock} / {@link ChunkPortalManager}). Same green swirl as
     * the {@link #LIMBO_PORTAL} (its blockstate reuses the limbo_portal models); no item form; unbreakable;
     * glows. Entirely dormant unless {@link Config#chunkPortalsEnabled()} is on.
     */
    public static final RegistryObject<Block> CHUNK_PORTAL = BLOCKS.register("chunk_portal",
            () -> new ChunkPortalBlock(BlockBehaviour.Properties.of()
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
                // === Village brain & node blocks (the brain node leads the whole tab) ===
                output.accept(VILLAGE_BRAIN_NODE_ITEM.get());
                output.accept(GOLEM_FACTORY_NODE_ITEM.get());
                output.accept(BARRACKS_NODE_ITEM.get());
                output.accept(LURE_TOWER_NODE_ITEM.get());
                output.accept(ModItems.GLOW_WAND.get()); // wand: highlights the mod's units + these node blocks
                // Early on purpose: it is the one block that lets a player tell the village to leave them
                // alone, and it is no use to anybody who never finds it.
                output.accept(NO_BUILD_SIGN_ITEM.get());

                // === Defensive / structural blocks ===
                output.accept(FAKE_COBBLESTONE_ITEM.get());
                output.accept(FAKE_OBSIDIAN_ITEM.get());
                // Straight after their Fake twins, so the two families sit side by side in the tab.
                output.accept(X_RAY_COBBLESTONE_ITEM.get());
                output.accept(X_RAY_OBSIDIAN_ITEM.get());
                output.accept(CHEAP_SCAFFOLD_ITEM.get());
                output.accept(REINFORCED_GLASS_ITEM.get());
                output.accept(PROTECTING_GLASS_ITEM.get());
                output.accept(OBSIDIAN_STAIRS_ITEM.get());
                output.accept(IRON_GOLEM_HEAD_ITEM.get());
                output.accept(ELEGANT_STAND_ITEM.get());
                output.accept(DEAD_BRAIN_NODE_ITEM.get());
                output.accept(DEBRIS_ITEM.get());

                // === TNT family ===
                output.accept(ORANGE_TNT_ITEM.get());
                output.accept(YELLOW_TNT_ITEM.get());
                output.accept(GREEN_TNT_ITEM.get());
                output.accept(BLUE_TNT_ITEM.get());
                output.accept(CYAN_TNT_ITEM.get());
                output.accept(PURPLE_TNT_ITEM.get());
                output.accept(BLACK_TNT_ITEM.get());

                // === Weapons, ammo & mace enchants ===
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

                // === Emerald gear & food (built from emeralds; stats between iron and diamond) -- in OUR tab ===
                output.accept(ModArmor.EMERALD_HELMET.get());
                output.accept(ModArmor.EMERALD_CHESTPLATE.get());
                output.accept(ModArmor.EMERALD_LEGGINGS.get());
                output.accept(ModArmor.EMERALD_BOOTS.get());
                for (RegistryObject<Item> tool : ModItems.EMERALD_TOOLS) output.accept(tool.get());
                output.accept(ModItems.EMERALD_APPLE.get());
                output.accept(ModItems.EMERALD_EGG.get());

                // === Potions ===
                output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(net.minecraft.world.item.Items.POTION), ModPotions.IMMUNITY.get())); // Potion of Immunity
                acceptPotionFamily(output, ModPotions.DARIUS_SPECIAL_EFFECT.get());       // Darius' Special Sauce
                acceptPotionFamily(output, ModPotions.DARIUS_SPECIAL_EFFECT_LONG.get());  // ...and the long one

                // === Emerald Virus / fertile-rich infection family (potion, arrows, then the block family) ===
                // No tipped arrow for these two: the Emerald Virus Arrow below is the mod's own arrow
                // for this effect, and listing vanilla's beside it put two near-identical arrows in
                // the tab. Vanilla makes a tipped arrow available for ANY registered potion, so the
                // "Arrow of Emerald Virus" still exists and still brews -- it is just not advertised
                // here next to the real one.
                acceptPotionFamily(output, ModPotions.EMERALD_VIRUS.get(), false);         // Emerald Virus
                acceptPotionFamily(output, ModPotions.EMERALD_VIRUS_STRONG.get(), false);  // Emerald Virus II
                output.accept(ModItems.EMERALD_VIRUS_ARROW.get());
                output.accept(ModItems.EMERALD_VIRUS_ARROW_2.get());
                // The whole fertile/rich family now lives in its own "VFB: blocks" tab: at five
                // colours it is 250 entries and would have swamped everything else in here.
                output.accept(ModItems.FERTILE_CHORUS.get());

                // === Spawn eggs: ONE cluster (village defenders, bosses, staged, then the Limbo creatures) ===
                output.accept(WALL_DEFENDER_SPAWN.get());
                output.accept(WALL_DEFENDER_STAGE4_SPAWN.get());
                output.accept(DEFENDER_GOLEM_SPAWN.get());
                for (RegistryObject<Item> egg : ModItems.STAGED_EGGS) output.accept(egg.get());
                output.accept(BRAIN_NODE_WARDEN_SPAWN.get());
                output.accept(ModItems.EMERALDGER_SPAWN.get());
                output.accept(ModItems.VINDICTIVE_GUARD_SPAWN.get());
                output.accept(ModItems.VINDICTIVE_GIGACHAD_SPAWN.get());
                output.accept(ModItems.GENERAL_DARIUS_SPAWN.get());
                output.accept(ModItems.RICH_SHEEP_SPAWN.get());
                output.accept(ModItems.LIMBO_PHANTOM_SPAWN.get());
                for (RegistryObject<Item> egg : ModItems.LIMBO_RAID_EGGS) output.accept(egg.get());

                // === Emerald faction (v1.0.3 mob infection) spawn eggs ===
                output.accept(ModItems.EMERALD_COW_SPAWN.get());
                output.accept(ModItems.EMERALD_CHICKEN_SPAWN.get());
                output.accept(ModItems.EMERALD_CAT_SPAWN.get());
                output.accept(ModItems.EMERALD_ENDERMITE_SPAWN.get());
                output.accept(ModItems.EMERALD_WOLF_SPAWN.get());
                output.accept(ModItems.EMERALD_HORSE_SPAWN.get());
                output.accept(ModItems.EMERALD_PIG_SPAWN.get());
                output.accept(ModItems.EMERALD_SALMON_SPAWN.get());
                output.accept(ModItems.EMERALD_BAT_SPAWN.get());
                output.accept(ModItems.EMERALD_ZOMBIE_SPAWN.get());
                output.accept(ModItems.EMERALD_SKELETON_SPAWN.get());
                output.accept(ModItems.EMERALD_CREEPER_SPAWN.get());
                output.accept(ModItems.EMERALD_ENDERMAN_SPAWN.get());
                output.accept(ModItems.EMERALD_IRON_GOLEM_SPAWN.get());
                output.accept(ModItems.EMERALD_DEFENDER_GOLEM_SPAWN.get());
                output.accept(ModItems.EMERALD_GIGACHAD_GOLEM_SPAWN.get());
                output.accept(ModItems.EMERALD_PHANTOM_SPAWN.get());
                output.accept(ModItems.EMERALD_WITHER_SPAWN.get());
                output.accept(ModItems.EMERALD_GUARDIAN_SPAWN.get());
                output.accept(ModItems.EMERALD_ELDER_GUARDIAN_SPAWN.get());

                // === Limbo cluster (the end): blocks -> materials -> potions -> armour -> tools ===
                output.accept(LIMBO_BLOCK_ITEM.get());
                output.accept(LIMBO_BED_ITEM.get());
                output.accept(LIMBO_GRAVEL_ITEM.get());
                output.accept(LIMBO_EGG_ITEM.get());
                output.accept(FAKE_BEDROCK_ITEM.get());
                output.accept(X_RAY_BEDROCK_ITEM.get());
                output.accept(FORGOTTEN_GLOWSTONE_ITEM.get());
                output.accept(ModItems.FORGOTTEN_POWDER.get());
                output.accept(ModItems.LIMBO_MEMBRANE.get());
                output.accept(ModItems.LIMBO_INGOT.get());
                output.accept(ModItems.LIMBO_SMITHING_TEMPLATE.get());
                output.accept(ModItems.LIMBO_POTION.get());
                output.accept(ModItems.VOLATILE_LIMBO_POTION.get());
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
                output.accept(ModArmor.GAS_MASK.get());
                output.accept(ModItems.LIMBO_SCYTHE.get());
                output.accept(ModItems.LIMBO_SHIELD.get());
                output.accept(ModItems.PALADIN_SHIELD.get());
                output.accept(ModItems.REPUTATION_BOOK.get());
                output.accept(ModItems.LIMBO_BOOK.get());
                output.accept(ModItems.OBLITERATOR.get());
                output.accept(OBLITERATION_FURNACE_ITEM.get());
                output.accept(MEMORIAL_ITEM.get());
                // Last on purpose: it is a joke, and it belongs at the very bottom of the tab.
                output.accept(ModItems.RICKROLL_PAINTING.get());
            })
            .build());

    /**
     * "VFB: blocks" -- the whole fertile/rich family, all five colours, in one place.
     *
     * <p>Its own tab because the family is 250 entries once the colours are counted, and dropping that into
     * the main tab would have buried the brain node, the gear and the Limbo line under a wall of dirt.
     *
     * <p>Ordered green first, then cyan, purple, orange, yellow, with each colour's blocks in the same
     * sequence -- so the five sets read as five copies of one shelf rather than 250 unrelated items.
     */
    public static final RegistryObject<CreativeModeTab> BLOCKS_TAB = TABS.register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + TheVillageFightsBack.MODID + ".blocks"))
                    .icon(() -> new ItemStack(INFESTED_PATH_ITEM.get()))
                    .withTabsBefore(TAB.getKey())
                    .displayItems((params, output) -> {
                        // --- green: the original set, in its long-standing order ---
                        for (RegistryObject<Item> it : FERTILE_RICH_ITEMS) output.accept(it.get());
                        output.accept(FERTILE_STAIRS_ITEM.get());
                        output.accept(FERTILE_SLAB_ITEM.get());
                        output.accept(FERTILE_FENCE_ITEM.get());
                        output.accept(FERTILE_IRON_BARS_ITEM.get());
                        output.accept(FERTILE_GRASS_ITEM.get());
                        output.accept(FERTILE_KELP_ITEM.get());
                        output.accept(INFESTED_PATH_ITEM.get());
                output.accept(VILLAGE_PATH_ITEM.get());
                        output.accept(FERTILE_PLANKS_ITEM.get());
                        output.accept(FERTILE_PLANKS_STAIRS_ITEM.get());
                        output.accept(FERTILE_PLANKS_SLAB_ITEM.get());
                        output.accept(FERTILE_COBBLESTONE_ITEM.get());
                        output.accept(FERTILE_DOOR_ITEM.get());
                        output.accept(FERTILE_CHORUS_PLANT_ITEM.get());
                        output.accept(FERTILE_CHORUS_FLOWER_ITEM.get());
                        output.accept(RICH_CHORUS_PLANT_ITEM.get());
                        output.accept(RICH_CHORUS_FLOWER_ITEM.get());
                        output.accept(FERTILE_SCULK_CATALYST_ITEM.get());
                        output.accept(RICH_SCULK_CATALYST_ITEM.get());

                        // --- the four colour sets, each in its registration order ---
                        for (String c : COLOUR_SUFFIXES) {
                            for (RegistryObject<Item> it : COLOURED_ITEMS.getOrDefault(
                                    c, java.util.List.of())) {
                                output.accept(it.get());
                            }
                        }
                    })
                    .build());

    /** Register the DeferredRegisters on the mod event bus (call from the mod constructor). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }    /**
     * Every form of one of our potions, for the creative tab: drinkable, splash, lingering, and the tipped
     * arrow. All four are brewable and all four already have names in the lang file, so listing only the
     * drinkable one meant the rest existed but could not be found without the vanilla search tab.
     */
    private static void acceptPotionFamily(net.minecraft.world.item.CreativeModeTab.Output output,
                                           net.minecraft.world.item.alchemy.Potion potion) {
        acceptPotionFamily(output, potion, true);
    }

    /** {@code withTippedArrow} false where the mod ships its own arrow item for the same effect. */
    private static void acceptPotionFamily(net.minecraft.world.item.CreativeModeTab.Output output,
                                           net.minecraft.world.item.alchemy.Potion potion,
                                           boolean withTippedArrow) {
        output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                new ItemStack(net.minecraft.world.item.Items.POTION), potion));
        output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                new ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), potion));
        output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                new ItemStack(net.minecraft.world.item.Items.LINGERING_POTION), potion));
        if (withTippedArrow) {
            output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                    new ItemStack(net.minecraft.world.item.Items.TIPPED_ARROW), potion));
        }
    }


}
