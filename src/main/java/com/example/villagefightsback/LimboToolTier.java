package com.example.villagefightsback;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Tool tier for Limbo gear -- a notch above netherite: more uses, faster, higher attack bonus, and harvest
 * level 4 (netherite tier, so it mines everything netherite can). The Limbo Pickaxe additionally smashes
 * bedrock + reinforced deepslate, but that is special-cased in {@link LimboPickaxeEvents} (those blocks have
 * -1 hardness and can never be mined by the normal loop).
 */
public enum LimboToolTier implements Tier {
    INSTANCE;

    @Override public int getUses() { return 2500; }            // > netherite (2031)
    @Override public float getSpeed() { return 10.0F; }         // > netherite (9.0)
    @Override public float getAttackDamageBonus() { return 4.0F; }
    @Override public int getLevel() { return 4; }               // netherite harvest level
    @Override public int getEnchantmentValue() { return 15; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(ModItems.LIMBO_MEMBRANE.get()); }
}
