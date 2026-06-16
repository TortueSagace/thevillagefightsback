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
