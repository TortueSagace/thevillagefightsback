package com.example.villagefightsback;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Emerald tool tier: between iron and diamond, mirroring the emerald armour.
 *  uses: iron 250, EMERALD 900, diamond 1561  |  speed: iron 6, EMERALD 7, diamond 8
 *  attack bonus: iron 2, EMERALD 2.5, diamond 3  |  harvest level: iron (2) -- below diamond (can't mine obsidian)
 */
public enum EmeraldTier implements Tier {
    INSTANCE;

    @Override public int getUses() { return 900; }
    @Override public float getSpeed() { return 7.0F; }
    @Override public float getAttackDamageBonus() { return 2.5F; }
    @Override public int getLevel() { return 2; }                 // iron-equivalent harvest level
    @Override public int getEnchantmentValue() { return 14; }     // enchants well, like the armour
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.EMERALD); }
}
