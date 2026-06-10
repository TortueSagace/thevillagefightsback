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
            () -> new BlockItem(FAKE_COBBLESTONE.get(), new Item.Properties()));

    /** The mod's own creative tab. New blocks/items get added here over time. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + VillagerGunners.MODID))
            .icon(() -> new ItemStack(VILLAGE_GRAVEMIND_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(VILLAGE_GRAVEMIND_ITEM.get());
                output.accept(CHEAP_SCAFFOLD_ITEM.get());
            })
            .build());

    /** Register the DeferredRegisters on the mod event bus (call from the mod constructor). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
