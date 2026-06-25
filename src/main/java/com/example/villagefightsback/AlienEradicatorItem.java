package com.example.villagefightsback;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;

/**
 * "Alien Eradicator": a stronger sibling of the {@link AlienSlasherItem}, a netherite axe re-forged with even
 * more potent emerald-block alien matter. Identical attack stats to a vanilla netherite axe (Tier NETHERITE,
 * +5 attack damage, -3 speed -> 10 damage), but it always shimmers (perma-glint) and its on-hit payload --
 * Emerald Virus II, Slowness IV, fire, and a huge knockback -- is applied in {@link EmeraldVirusEvents#onLivingHurt}
 * so it triggers for players AND axe-wielding villagers alike (mobs don't route melee through {@link #hurtEnemy}).
 * The only difference from the Alien Slasher is that it inflicts Emerald Virus II (Wither II) instead of Virus I.
 */
public class AlienEradicatorItem extends AxeItem {
    public AlienEradicatorItem(Properties properties) {
        // Vanilla netherite axe values: +5 attack damage, -3.0 attack speed. In 1.21 the attack stats are baked
        // into Item.Properties via AxeItem.createAttributes(tier, dmg, speed).
        super(Tiers.NETHERITE, properties.attributes(AxeItem.createAttributes(Tiers.NETHERITE, 5.0F, -3.0F)));
    }

    /** Always shimmer so the blade looks "alive" and glows, even with no enchantments. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // Netherite-grade fire resistance is now set via Item.Properties.fireResistant() in ModItems (the Item
    // isFireResistant() override was removed in 1.21).
}
