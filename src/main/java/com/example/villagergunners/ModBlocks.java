package com.example.villagergunners;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block/item registration. */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, VillagerGunners.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VillagerGunners.MODID);

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

    /** Register both DeferredRegisters on the mod event bus (call from the mod constructor). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(VILLAGE_GRAVEMIND_ITEM);
        }
    }
}
