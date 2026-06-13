package com.example.villagergunners;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * "Limbo" armour material: an end-game upgrade of netherite, forged by smithing a netherite piece with a
 * Limbo Ingot + a Limbo Smithing Template. Better protection and toughness than netherite, and -- uniquely --
 * COMPLETE knockback resistance ({@link #getKnockbackResistance()} == 1.0). The full-set bonuses (immunity,
 * fire/fall immunity, phantom neutrality, the banish beam) are layered on top by {@link LimboGearEvents}.
 *
 * The worn-armour textures live at {@code textures/models/armor/limbo_layer_1.png} / {@code _layer_2.png}.
 */
public final class LimboArmorMaterial implements ArmorMaterial {
    public static final LimboArmorMaterial INSTANCE = new LimboArmorMaterial();
    private static final int DURABILITY_MULT = 45; // > netherite (37)

    private LimboArmorMaterial() {}

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        int base = switch (type) {
            case BOOTS -> 13;
            case LEGGINGS -> 15;
            case CHESTPLATE -> 16;
            case HELMET -> 11;
        };
        return base * DURABILITY_MULT;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        // Stronger than netherite (3/6/8/3 = 20); limbo is 4/7/9/4 = 24.
        return switch (type) {
            case BOOTS -> 4;
            case LEGGINGS -> 7;
            case CHESTPLATE -> 9;
            case HELMET -> 4;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ModItems.LIMBO_INGOT.get());
    }

    @Override
    public String getName() {
        return VillagerGunners.MODID + ":limbo";
    }

    @Override
    public float getToughness() {
        return 4.0F; // > netherite (3.0)
    }

    @Override
    public float getKnockbackResistance() {
        return 1.0F; // complete knockback immunity
    }
}
