package com.example.villagefightsback;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
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
    public int getDurabilityForSlot(EquipmentSlot slot) {
        int base = switch (slot) {
            case FEET -> 13;
            case LEGS -> 15;
            case CHEST -> 16;
            case HEAD -> 11;
            default -> 0;
        };
        return base * DURABILITY_MULT;
    }

    @Override
    public int getDefenseForSlot(EquipmentSlot slot) {
        // Stronger than netherite (3/6/8/3 = 20); limbo is 4/7/9/4 = 24.
        return switch (slot) {
            case FEET -> 4;
            case LEGS -> 7;
            case CHEST -> 9;
            case HEAD -> 4;
            default -> 0;
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
        return Ingredient.of(ModItems.LIMBO_MEMBRANE.get()); // repair Limbo armour with Limbo Membrane
    }

    @Override
    public String getName() {
        return TheVillageFightsBack.MODID + ":limbo";
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
