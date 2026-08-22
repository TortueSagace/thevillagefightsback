package com.example.villagefightsback;

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
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_CHESTPLATE = ITEMS.register("emerald_chestplate",
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_LEGGINGS = ITEMS.register("emerald_leggings",
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_BOOTS = ITEMS.register("emerald_boots",
            () -> new ArmorItem(EmeraldArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS, new Item.Properties()));

    // --- Limbo gear: smithed from netherite + a Limbo Ingot + a Limbo Smithing Template. Better than
    //     netherite, full knockback immunity (material), and -- as a worn SET -- the bonuses in LimboGearEvents.
    //     Pieces are fireResistant() so they survive lava like netherite. ---
    public static final RegistryObject<Item> LIMBO_HELMET = ITEMS.register("limbo_helmet",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> LIMBO_CHESTPLATE = ITEMS.register("limbo_chestplate",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> LIMBO_LEGGINGS = ITEMS.register("limbo_leggings",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, ArmorItem.Type.LEGGINGS, new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> LIMBO_BOOTS = ITEMS.register("limbo_boots",
            () -> new LimboArmorItem(LimboArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS, new Item.Properties().fireResistant()));

    // A Limbo chestplate that also glides like an elytra (combine a Limbo Chestplate + an Elytra). Counts as
    // the chest piece of the Limbo set (LimboGear). Wings render via the vanilla ElytraLayer (canElytraFly).
    public static final RegistryObject<Item> LIMBO_ELYTRA_CHESTPLATE = ITEMS.register("limbo_elytra_chestplate",
            () -> new LimboElytraChestplateItem(LimboArmorMaterial.INSTANCE, new Item.Properties().fireResistant()));

    /** Call from the mod constructor. */
    /**
     * The Gas Mask: leather stats, and the only thing that keeps the Limbo's air out of you.
     *
     * <p>Deliberately uncraftable -- it is found, at 2% in a village's chests, or not at all.
     */
    public static final RegistryObject<Item> GAS_MASK = ITEMS.register("gas_mask",
            () -> new ArmorItem(GasMaskArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    // The emerald armour lives in OUR creative tab (built from emeralds), added in ModBlocks' tab contents --
    // not the vanilla Combat tab.
}
