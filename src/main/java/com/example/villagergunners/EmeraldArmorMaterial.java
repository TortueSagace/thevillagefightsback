package com.example.villagergunners;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Emerald armour: tougher than iron, weaker than diamond. Repaired with emeralds, enchantable like normal
 * armour. (The fire-/blast-protection enchants the stage-3 guards get are applied when the guard equips the
 * piece, not baked into the item.)
 *
 *  protection (boots/legs/chest/helmet): iron 2/5/6/2, EMERALD 2/5/7/2, diamond 3/6/8/3
 *  durability mult: iron 15, EMERALD 24, diamond 33   |   toughness: iron 0, EMERALD 1, diamond 2
 */
public final class EmeraldArmorMaterial implements ArmorMaterial {
    public static final EmeraldArmorMaterial INSTANCE = new EmeraldArmorMaterial();

    private static final int DURABILITY_MULT = 24;
    private EmeraldArmorMaterial() {}

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
        return switch (type) {
            case BOOTS -> 2;
            case LEGGINGS -> 5;
            case CHESTPLATE -> 7;
            case HELMET -> 2;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 12; // iron 9, diamond 10 -> a touch better (emeralds enchant nicely)
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.EMERALD);
    }

    @Override
    public String getName() {
        // namespaced so Forge looks for the worn-layer textures under our mod: textures/models/armor/emerald_layer_*.png
        return VillagerGunners.MODID + ":emerald";
    }

    @Override
    public float getToughness() {
        return 1.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
