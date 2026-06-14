package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Immunity": a beneficial effect whose holder takes NO damage while it is active (enforced in
 * {@link VillageEvents}'s attack handler, except for invulnerability-bypassing sources like /kill or the void).
 * A Village Brain Node grants it to nearby villagers and iron golems; it is also obtainable as a potion and via
 * {@code /effect give @s thevillagefightsback:immunity}. The colour is only the inventory-icon tint.
 */
public class ImmunityEffect extends MobEffect {
    public ImmunityEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFE45C); // gold
    }
}
