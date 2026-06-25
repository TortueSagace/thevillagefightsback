package com.example.villagefightsback;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;

/**
 * "Alien Slasher": a netherite axe re-forged with emerald-block alien matter. Identical attack stats to a
 * vanilla netherite axe (Tier NETHERITE, +5 attack damage, -3 speed -> 10 damage), but it always shimmers
 * (perma-glint) and its on-hit payload -- Emerald Virus, Slowness IV, fire, and a huge knockback -- is
 * applied in {@link EmeraldVirusEvents#onLivingHurt} so it triggers for players AND axe-wielding villagers
 * alike (mobs don't route melee through {@link #hurtEnemy}).
 */
public class AlienSlasherItem extends AxeItem {
    public AlienSlasherItem(Properties properties) {
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
