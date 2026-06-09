package com.example.villagergunners;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block/item registration. The "Inverted Respawn Anchor" is a recolored copy of the vanilla respawn
 * anchor: it charges with glowstone, shows all 5 charge states (inverted textures), emits light, and
 * explodes outside the Nether. NOTE: Minecraft hardcodes the death-respawn to the vanilla anchor
 * (PlayerList checks blockstate.is(Blocks.RESPAWN_ANCHOR)), so dying does NOT yet return you here --
 * that needs custom respawn handling, which can be added separately.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, VillagerGunners.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VillagerGunners.MODID);

    public static final RegistryObject<Block> INVERTED_RESPAWN_ANCHOR = BLOCKS.register(
            "inverted_respawn_anchor",
            () -> new RespawnAnchorBlock(BlockBehaviour.Properties.copy(Blocks.RESPAWN_ANCHOR)));

    public static final RegistryObject<Item> INVERTED_RESPAWN_ANCHOR_ITEM = ITEMS.register(
            "inverted_respawn_anchor",
            () -> new BlockItem(INVERTED_RESPAWN_ANCHOR.get(), new Item.Properties()));

    /** Register both DeferredRegisters on the mod event bus (call from the mod constructor). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    /** Put the block in the Functional Blocks creative tab (next to the vanilla anchor). */
    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(INVERTED_RESPAWN_ANCHOR_ITEM);
        }
    }
}
