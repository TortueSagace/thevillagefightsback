package com.example.villagefightsback;

import net.minecraft.world.item.ItemStack;

/**
 * The Alien Smasher: a Limbo-forged upgrade of our native {@link MaceItem} (Mace + Limbo Ingot at a smithing
 * table). It IS a mace -- the SMASH attack, the mace enchantments, and any further mace enchanting all apply
 * automatically (it inherits {@link MaceItem}'s attributes and the {@code MaceItem instanceof} category). It
 * always shimmers (perma-glint), wears the dark-Limbo green-animated icon, and carries a guaranteed baseline
 * of the guards' enchanted-mace power (Density 3 + Wind Burst 3 + Fire Aspect 2, enforced in
 * {@link MaceCombatEvents}) plus a signature on-hit effect: a triple, no-creature-damage block-and-fire blast
 * stacked beneath the target (carving a hole it falls into), implemented in {@link AlienSmasherEvents}.
 */
public class AlienSmasherItem extends MaceItem {
    public AlienSmasherItem(Properties properties) {
        // Inherit the Mace base (durability 500 / EPIC / enchant value 15 / attributes) for an identical
        // mace feel, then add the special on top. The "fireResistant" Limbo nature is set in ModItems.
        super(properties);
    }

    /** Always shimmer so the alien matter looks "alive", even before the player adds any enchantments. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
