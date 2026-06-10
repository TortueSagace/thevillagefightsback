package com.example.villagergunners;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Plain item registration (non-block items). */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VillagerGunners.MODID);

    /** The Alien Slasher melee weapon -- a fancier, virus-spreading netherite axe ({@link AlienSlasherItem}). */
    public static final RegistryObject<Item> ALIEN_SLASHER =
            ITEMS.register("alien_slasher", () -> new AlienSlasherItem(new Item.Properties().fireResistant()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
