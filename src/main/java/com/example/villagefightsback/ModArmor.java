package com.example.villagefightsback;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The emerald armour set ({@link EmeraldArmorMaterial}). Registered on its own DeferredRegister. */
public final class ModArmor {
    private ModArmor() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TheVillageFightsBack.MODID);

    public static final RegistryObject<Item> EMERALD_HELMET = ITEMS.register("emerald_helmet",
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, EquipmentSlot.HEAD, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_CHESTPLATE = ITEMS.register("emerald_chestplate",
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, EquipmentSlot.CHEST, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_LEGGINGS = ITEMS.register("emerald_leggings",
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, EquipmentSlot.LEGS, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_BOOTS = ITEMS.register("emerald_boots",
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, EquipmentSlot.FEET, new Item.Properties()));

    // --- Limbo gear: smithed from netherite + a Limbo Ingot + a Limbo Smithing Template. Better than
    //     netherite, full knockback immunity (material), and -- as a worn SET -- the bonuses in LimboGearEvents.
    //     Pieces are fireResistant() so they survive lava like netherite. ---
    public static final RegistryObject<Item> LIMBO_HELMET = ITEMS.register("limbo_helmet",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, EquipmentSlot.HEAD, new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> LIMBO_CHESTPLATE = ITEMS.register("limbo_chestplate",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, EquipmentSlot.CHEST, new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> LIMBO_LEGGINGS = ITEMS.register("limbo_leggings",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, EquipmentSlot.LEGS, new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> LIMBO_BOOTS = ITEMS.register("limbo_boots",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, EquipmentSlot.FEET, new Item.Properties().fireResistant()));

    // A Limbo chestplate that also glides like an elytra (combine a Limbo Chestplate + an Elytra). Counts as
    // the chest piece of the Limbo set (LimboGear). Wings render via the vanilla ElytraLayer (canElytraFly).
    public static final RegistryObject<Item> LIMBO_ELYTRA_CHESTPLATE = ITEMS.register("limbo_elytra_chestplate",
            () -> new LimboElytraChestplateItem(LimboArmorMaterial.INSTANCE, new Item.Properties().fireResistant()));

    /** Call from the mod constructor. */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    // The emerald armour lives in OUR creative tab (built from emeralds), added in ModBlocks' tab contents --
    // not the vanilla Combat tab.
}
