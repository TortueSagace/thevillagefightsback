package com.example.villagefightsback;

import java.util.List;
import java.util.Map;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * "Limbo" armour material: an end-game upgrade of netherite, forged by smithing a netherite piece with a
 * Limbo Ingot + a Limbo Smithing Template. Better protection and toughness than netherite, and -- uniquely --
 * COMPLETE knockback resistance (knockback resistance == 1.0). The full-set bonuses (immunity,
 * fire/fall immunity, phantom neutrality, the banish beam) are layered on top by {@link LimboGearEvents}.
 *
 * The worn-armour textures live at {@code textures/models/armor/limbo_layer_1.png} / {@code _layer_2.png}.
 *
 * In 1.21 {@code ArmorMaterial} is a registered record exposed as a {@code Holder<ArmorMaterial>}; the holder
 * is registered in {@link ModArmor} and aliased here as {@link #INSTANCE} so the old references keep working.
 */
public final class LimboArmorMaterial {
    private LimboArmorMaterial() {}

    /** The registered material holder (registered in {@link ModArmor}). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> INSTANCE = ModArmor.LIMBO_MATERIAL;

    private static final int DURABILITY_MULT = 45; // > netherite (37)

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

    /** Builds the limbo {@link ArmorMaterial} record. Called from {@link ModArmor}. */
    public static ArmorMaterial create() {
        return new ArmorMaterial(
                // Stronger than netherite (3/6/8/3 = 20); limbo is 4/7/9/4 = 24.
                Map.of(
                        ArmorItem.Type.BOOTS, 4,
                        ArmorItem.Type.LEGGINGS, 7,
                        ArmorItem.Type.CHESTPLATE, 9,
                        ArmorItem.Type.HELMET, 4),
                15, // enchantment value
                SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(ModItems.LIMBO_MEMBRANE.get()), // repair Limbo armour with Limbo Membrane
                List.of(new ArmorMaterial.Layer(TheVillageFightsBack.id("limbo"))),
                4.0F,  // toughness > netherite (3.0)
                1.0F); // complete knockback immunity
    }
}
