package com.example.villagefightsback;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

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
    // 1.21: harvest level is expressed as the tag of blocks this tier CANNOT mine. Iron-equivalent: cannot mine
    // the diamond-only blocks (obsidian etc.), so it uses the iron "incorrect" tag.
    @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
    @Override public int getEnchantmentValue() { return 14; }     // enchants well, like the armour
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.EMERALD); }
}
