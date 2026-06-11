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

    /** The iron golem's rocket launcher -- a native clone of JEG's, held for the visual ({@link RocketEntity}). */
    public static final RegistryObject<Item> ROCKET_LAUNCHER =
            ITEMS.register("rocket_launcher", () -> new Item(new Item.Properties().stacksTo(1).fireResistant()));

    /** The flying rocket's billboard sprite (the projectile renders this; not obtainable on its own). */
    public static final RegistryObject<Item> ROCKET =
            ITEMS.register("rocket", () -> new Item(new Item.Properties()));

    /** Creative spawn egg for the Emeraldger (the stage-4 virus warrior). */
    public static final RegistryObject<Item> EMERALDGER_SPAWN =
            ITEMS.register("emeraldger_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.EMERALDGER, 0x17DD62, 0x0A6E2E, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
