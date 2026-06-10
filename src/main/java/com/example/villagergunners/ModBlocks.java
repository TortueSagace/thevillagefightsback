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
import net.minecraft.world.level.block.HalfTransparentBlock;
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
     * needs_diamond_tool tags). HalfTransparentBlock gives proper glass face-culling.
     */
    public static final RegistryObject<Block> REINFORCED_GLASS = BLOCKS.register(
            "reinforced_glass",
            () -> new HalfTransparentBlock(BlockBehaviour.Properties.of()
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
     * Wall defender: a conduit look-alike auto-turret (see {@link WallDefenderBlock} /
     * {@link WallDefenderBlockEntity}). 100 HP, fires a ~10-damage arrow/second at the nearest hostile.
     * Tough and glowing; the Gravemind drops one in a protecting-glass shell above each gate at level 2.
     */
    public static final RegistryObject<Block> WALL_DEFENDER = BLOCKS.register(
            "wall_defender",
            () -> new WallDefenderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(s -> 10)
                    .noOcclusion()));

    public static final RegistryObject<Item> WALL_DEFENDER_ITEM = ITEMS.register(
            "wall_defender",
            () -> new BlockItem(WALL_DEFENDER.get(), new Item.Properties()));

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
                output.accept(WALL_DEFENDER_ITEM.get());
            })
            .build());

    /** Register the DeferredRegisters on the mod event bus (call from the mod constructor). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
