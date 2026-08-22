package com.example.villagefightsback;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The emerald armour set ({@link EmeraldArmorMaterial}). Registered on its own DeferredRegister. */
public final class ModArmor {
    private ModArmor() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, TheVillageFightsBack.MODID);

    // --- Armour materials: in 1.21 ArmorMaterial is a registered record exposed as a Holder. ---
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(BuiltInRegistries.ARMOR_MATERIAL, TheVillageFightsBack.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> EMERALD_MATERIAL =
            ARMOR_MATERIALS.register("emerald", EmeraldArmorMaterial::create);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> LIMBO_MATERIAL =
            ARMOR_MATERIALS.register("limbo", LimboArmorMaterial::create);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK_MATERIAL =
            ARMOR_MATERIALS.register("gas_mask", GasMaskArmorMaterial::create);

    public static final DeferredHolder<Item, ArmorItem> EMERALD_HELMET = ITEMS.register("emerald_helmet",
            () -> new ArmorItem(EMERALD_MATERIAL, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(EmeraldArmorMaterial.durabilityForType(ArmorItem.Type.HELMET))));
    public static final DeferredHolder<Item, ArmorItem> EMERALD_CHESTPLATE = ITEMS.register("emerald_chestplate",
            () -> new ArmorItem(EMERALD_MATERIAL, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(EmeraldArmorMaterial.durabilityForType(ArmorItem.Type.CHESTPLATE))));
    public static final DeferredHolder<Item, ArmorItem> EMERALD_LEGGINGS = ITEMS.register("emerald_leggings",
            () -> new ArmorItem(EMERALD_MATERIAL, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(EmeraldArmorMaterial.durabilityForType(ArmorItem.Type.LEGGINGS))));
    public static final DeferredHolder<Item, ArmorItem> EMERALD_BOOTS = ITEMS.register("emerald_boots",
            () -> new ArmorItem(EMERALD_MATERIAL, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(EmeraldArmorMaterial.durabilityForType(ArmorItem.Type.BOOTS))));

    // --- Limbo gear: smithed from netherite + a Limbo Ingot + a Limbo Smithing Template. Better than
    //     netherite, full knockback immunity (material), and -- as a worn SET -- the bonuses in LimboGearEvents.
    //     Pieces are fireResistant() so they survive lava like netherite. ---
    public static final DeferredHolder<Item, LimboArmorItem> LIMBO_HELMET = ITEMS.register("limbo_helmet",
            () -> new LimboArmorItem(LIMBO_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().fireResistant()
                    .durability(LimboArmorMaterial.durabilityForType(ArmorItem.Type.HELMET))));
    public static final DeferredHolder<Item, LimboArmorItem> LIMBO_CHESTPLATE = ITEMS.register("limbo_chestplate",
            () -> new LimboArmorItem(LIMBO_MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().fireResistant()
                    .durability(LimboArmorMaterial.durabilityForType(ArmorItem.Type.CHESTPLATE))));
    public static final DeferredHolder<Item, LimboArmorItem> LIMBO_LEGGINGS = ITEMS.register("limbo_leggings",
            () -> new LimboArmorItem(LIMBO_MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties().fireResistant()
                    .durability(LimboArmorMaterial.durabilityForType(ArmorItem.Type.LEGGINGS))));
    public static final DeferredHolder<Item, LimboArmorItem> LIMBO_BOOTS = ITEMS.register("limbo_boots",
            () -> new LimboArmorItem(LIMBO_MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties().fireResistant()
                    .durability(LimboArmorMaterial.durabilityForType(ArmorItem.Type.BOOTS))));

    // A Limbo chestplate that also glides like an elytra (combine a Limbo Chestplate + an Elytra). Counts as
    // the chest piece of the Limbo set (LimboGear). Wings render via the vanilla ElytraLayer (canElytraFly).
    public static final DeferredHolder<Item, LimboElytraChestplateItem> LIMBO_ELYTRA_CHESTPLATE = ITEMS.register("limbo_elytra_chestplate",
            () -> new LimboElytraChestplateItem(LIMBO_MATERIAL, new Item.Properties().fireResistant()
                    .durability(LimboArmorMaterial.durabilityForType(ArmorItem.Type.CHESTPLATE))));

    /** Call from the mod constructor. */
    /**
     * The Gas Mask: leather stats, and the only thing that keeps the Limbo's air out of you.
     *
     * <p>Deliberately uncraftable -- it is found, at 2% in a village's chests, or not at all.
     */
    public static final DeferredHolder<Item, Item> GAS_MASK = ITEMS.register("gas_mask",
            () -> new ArmorItem(GAS_MASK_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties()
                    .durability(GasMaskArmorMaterial.durabilityForType(ArmorItem.Type.HELMET))));

    public static void register(IEventBus modBus) {
        ARMOR_MATERIALS.register(modBus);
        ITEMS.register(modBus);
    }

    // The emerald armour lives in OUR creative tab (built from emeralds), added in ModBlocks' tab contents --
    // not the vanilla Combat tab.
}
