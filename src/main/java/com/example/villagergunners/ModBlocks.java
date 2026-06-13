package com.example.villagergunners;

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
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, VillagerGunners.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VillagerGunners.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VillagerGunners.MODID);

    /**
     * The Village Gravemind: a recolored respawn-anchor look-alike that drives the village's global
     * evolution level. It glows brighter as the level rises (charges visual). Tough but breakable.
     */
    public static final RegistryObject<Block> VILLAGE_GRAVEMIND = BLOCKS.register(
            "village_gravemind",
            () -> new VillageGravemindBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(s -> s.getValue(VillageGravemindBlock.CHARGES) * 3)));

    public static final RegistryObject<Item> VILLAGE_GRAVEMIND_ITEM = ITEMS.register(
            "village_gravemind",
            () -> new BlockItem(VILLAGE_GRAVEMIND.get(), new Item.Properties()));

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
                    "block.gobacktoyourworld.fake_cobblestone.desc"));

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
     * can exit. Reinforced-glass tough (hardness 30, blast resistance 1200, diamond pickaxe).
     */
    public static final RegistryObject<Block> PROTECTING_GLASS = BLOCKS.register(
            "protecting_glass",
            () -> new ProtectingGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(30.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
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
     * real JEG bullet/second at the nearest hostile). The Gravemind also spawns one above each gate at level 2.
     */
    public static final RegistryObject<Item> WALL_DEFENDER_SPAWN = ITEMS.register(
            "wall_defender",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.WALL_DEFENDER,
                    0x1b4a4a, 0x78ebf5, new Item.Properties()));

    /** Spawn egg for the {@link GravemindWardenEntity} (a green Warden with a gravemind-block head). */
    public static final RegistryObject<Item> GRAVEMIND_WARDEN_SPAWN = ITEMS.register(
            "gravemind_warden",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.UNLEASHED_GRAVEMIND,
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
    }

    private static void registerFertileRich(String base, Block vanilla, FertileKind kind) {
        registerFertileBlock("fertile_" + base, vanilla, kind);
        registerFertileBlock("rich_" + base, vanilla, kind);
    }

    private static void registerFertileBlock(String name, Block vanilla, FertileKind kind) {
        // if/else, not switch-over-enum, to avoid generating a synthetic switch-map class.
        RegistryObject<Block> block = BLOCKS.register(name, () -> {
            BlockBehaviour.Properties p = BlockBehaviour.Properties.copy(vanilla);
            if (kind == FertileKind.FALLING) return new FallingBlock(p);
            if (kind == FertileKind.LOG) return new RotatedPillarBlock(p);
            if (kind == FertileKind.LEAVES) {
                // A plain (non-LeavesBlock) block so infected leaves NEVER decay; copy() carries leaves'
                // non-occlusion. The model is cutout_mipped so the leaf gaps show.
                return new Block(p.noOcclusion());
            }
            if (kind == FertileKind.SCULK) {
                // Render fullbright like vanilla sculk so the texture glows in the dark (copy() doesn't
                // carry emissiveRendering over). The block still emits no light into the world.
                p.emissiveRendering((s, l, pos) -> true);
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

    // Fertile / Rich sculk catalyst: on a nearby mob death it spreads fertile / rich sculk like vanilla
    // sculk spreads from a catalyst (see SculkCatalystEvents). Glows like the sculk family.
    public static final RegistryObject<Block> FERTILE_SCULK_CATALYST = BLOCKS.register("fertile_sculk_catalyst",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SCULK_CATALYST).emissiveRendering((s, l, p) -> true)));
    public static final RegistryObject<Block> RICH_SCULK_CATALYST = BLOCKS.register("rich_sculk_catalyst",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SCULK_CATALYST).emissiveRendering((s, l, p) -> true)));
    public static final RegistryObject<Item> FERTILE_SCULK_CATALYST_ITEM = ITEMS.register("fertile_sculk_catalyst",
            () -> new BlockItem(FERTILE_SCULK_CATALYST.get(), new Item.Properties()));
    public static final RegistryObject<Item> RICH_SCULK_CATALYST_ITEM = ITEMS.register("rich_sculk_catalyst",
            () -> new BlockItem(RICH_SCULK_CATALYST.get(), new Item.Properties()));

    /** Fertile door: the infection replaces every (wooden) door with this. Oak-door shape, green-tinted. */
    public static final RegistryObject<Block> FERTILE_DOOR = BLOCKS.register("fertile_door",
            () -> new net.minecraft.world.level.block.DoorBlock(
                    BlockBehaviour.Properties.copy(Blocks.OAK_DOOR).noOcclusion(),
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

    /** The mod's own creative tab. New blocks/items get added here over time. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + VillagerGunners.MODID))
            .icon(() -> new ItemStack(VILLAGE_GRAVEMIND_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(VILLAGE_GRAVEMIND_ITEM.get());
                output.accept(CHEAP_SCAFFOLD_ITEM.get());
                output.accept(FAKE_COBBLESTONE_ITEM.get());
                output.accept(BLUE_TNT_ITEM.get());
                output.accept(GREEN_TNT_ITEM.get());
                output.accept(REINFORCED_GLASS_ITEM.get());
                output.accept(PROTECTING_GLASS_ITEM.get());
                output.accept(WALL_DEFENDER_SPAWN.get());
                output.accept(GRAVEMIND_WARDEN_SPAWN.get());
                output.accept(ModItems.EMERALDGER_SPAWN.get());
                for (RegistryObject<Item> egg : ModItems.STAGED_EGGS) output.accept(egg.get());
                output.accept(ModItems.ALIEN_SLASHER.get());
                output.accept(ModItems.ROCKET_LAUNCHER.get());
                // Emerald gear (built from emeralds; stats between iron and diamond) -- in OUR tab.
                output.accept(ModArmor.EMERALD_HELMET.get());
                output.accept(ModArmor.EMERALD_CHESTPLATE.get());
                output.accept(ModArmor.EMERALD_LEGGINGS.get());
                output.accept(ModArmor.EMERALD_BOOTS.get());
                for (RegistryObject<Item> tool : ModItems.EMERALD_TOOLS) output.accept(tool.get());
                output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(net.minecraft.world.item.Items.POTION), ModPotions.IMMUNITY.get())); // Potion of Immunity
                output.accept(ModItems.EMERALD_APPLE.get());
                output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(net.minecraft.world.item.Items.POTION), ModPotions.EMERALD_VIRUS.get())); // Potion of Emerald Virus
                for (RegistryObject<Item> it : FERTILE_RICH_ITEMS) output.accept(it.get());
                output.accept(FERTILE_STAIRS_ITEM.get());
                output.accept(FERTILE_SLAB_ITEM.get());
                output.accept(FERTILE_FENCE_ITEM.get());
                output.accept(FERTILE_DOOR_ITEM.get());
                output.accept(FERTILE_SCULK_CATALYST_ITEM.get());
                output.accept(RICH_SCULK_CATALYST_ITEM.get());
                output.accept(LIMBO_BED_ITEM.get());
                output.accept(LIMBO_GRAVEL_ITEM.get());
                output.accept(ModItems.LIMBO_POTION.get());
                output.accept(ModItems.LIMBO_ARROW.get());
                output.accept(ModItems.EMERALD_VIRUS_ARROW.get());
            })
            .build());

    /** Register the DeferredRegisters on the mod event bus (call from the mod constructor). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
