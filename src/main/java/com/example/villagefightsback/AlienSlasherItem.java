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
        // Vanilla netherite axe values: AxeItem(Tiers.NETHERITE, 5.0F, -3.0F, props).
        super(Tiers.NETHERITE, 5.0F, -3.0F, properties);
    }

    /** Always shimmer so the blade looks "alive" and glows, even with no enchantments. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Netherite-grade: survives lava/fire like the axe it was forged from. */
    @Override
    public boolean isFireResistant() {
        return true;
    }
}
