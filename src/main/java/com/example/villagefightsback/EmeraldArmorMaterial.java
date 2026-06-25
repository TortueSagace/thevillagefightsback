package com.example.villagefightsback;

import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Emerald armour: tougher than iron, weaker than diamond. Repaired with emeralds, enchantable like normal
 * armour. (The fire-/blast-protection enchants the stage-3 guards get are applied when the guard equips the
 * piece, not baked into the item.)
 *
 *  protection (boots/legs/chest/helmet): iron 2/5/6/2, EMERALD 2/5/7/2, diamond 3/6/8/3
 *  durability mult: iron 15, EMERALD 24, diamond 33   |   toughness: iron 0, EMERALD 1, diamond 2
 *
 * In 1.21 {@code ArmorMaterial} is a registered record exposed as a {@code Holder<ArmorMaterial>}; the holder
 * is registered in {@link ModArmor} and aliased here as {@link #INSTANCE} so the old references keep working.
 */
public final class EmeraldArmorMaterial {
    private EmeraldArmorMaterial() {}

    /** The registered material holder (registered in {@link ModArmor}). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> INSTANCE = ModArmor.EMERALD_MATERIAL;

    private static final int DURABILITY_MULT = 24;

    /** Per-piece base durability (multiplied by {@link #DURABILITY_MULT}); applied via Item.Properties. */
    public static int durabilityForType(ArmorItem.Type type) {
        int base = switch (type) {
            case BOOTS -> 13;
            case LEGGINGS -> 15;
            case CHESTPLATE -> 16;
            case HELMET -> 11;
            case BODY -> 16;
        };
        return base * DURABILITY_MULT;
    }

    /** Builds the emerald {@link ArmorMaterial} record. Called from {@link ModArmor}. */
    public static ArmorMaterial create() {
        return new ArmorMaterial(
                Map.of(
                        ArmorItem.Type.BOOTS, 2,
                        ArmorItem.Type.LEGGINGS, 5,
                        ArmorItem.Type.CHESTPLATE, 7,
                        ArmorItem.Type.HELMET, 2),
                12, // enchantment value: iron 9, diamond 10 -> a touch better (emeralds enchant nicely)
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(Items.EMERALD),
                // namespaced layer so the worn-layer textures load from our mod:
                //   textures/models/armor/emerald_layer_1.png / _layer_2.png
                List.of(new ArmorMaterial.Layer(TheVillageFightsBack.id("emerald"))),
                1.0F,  // toughness
                0.0F); // knockback resistance
    }
}
