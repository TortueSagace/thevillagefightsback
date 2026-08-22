package com.example.villagefightsback;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The Gas Mask's material: leather, exactly.
 *
 * <p>Same durability multiplier, same 1 point of protection, same enchantability, same repair item. It is a
 * filter, not a helmet -- what it is worth is that it keeps the Limbo's air out of you
 * ({@link SlowAnnihilationEvents}), and giving it armour value on top of that would quietly make it the best
 * head slot in the mod for anyone who found one in a chest.
 *
 * <p>It exists as its own material for one reason: {@code getName()} is what Forge builds the worn-layer
 * texture path from, so borrowing {@code ArmorMaterials.LEATHER} would send it looking for
 * {@code minecraft:textures/models/armor/leather_layer_1.png} and the wearer would be walking around in a
 * leather cap.
 */
public final class GasMaskArmorMaterial implements ArmorMaterial {
    public static final GasMaskArmorMaterial INSTANCE = new GasMaskArmorMaterial();

    /** Leather's multiplier. */
    private static final int DURABILITY_MULT = 5;

    private GasMaskArmorMaterial() {}

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 13;
            case LEGGINGS -> 15;
            case CHESTPLATE -> 16;
            case HELMET -> 11;
        } * DURABILITY_MULT;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 1;
            case LEGGINGS -> 2;
            case CHESTPLATE -> 3;
            case HELMET -> 1;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 15;   // leather's
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.LEATHER);
    }

    @Override
    public String getName() {
        // -> textures/models/armor/gas_mask_layer_1.png
        return TheVillageFightsBack.MODID + ":gas_mask";
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
