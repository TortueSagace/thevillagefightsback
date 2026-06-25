package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Enemy of the Village": a marker status placed on a player who attacked the village -- by mining a Village
 * Brain Node / Emerald Node, or by harming a villager / guard / iron golem. While a player holds it, every
 * nearby defender hunts them on sight (see {@link VillageEvents#getAggroPlayer}). It lasts 20 minutes and is
 * cleared on death; re-offending refreshes the timer. It has no per-tick behaviour of its own and is applied
 * with particles off (icon only -- the bad-omen icon).
 */
public class EnemyOfVillageEffect extends MobEffect {
    public EnemyOfVillageEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8B1A1A); // dark red (colour is unused while particles are off)
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false; // purely a marker -- never runs applyEffectTick
    }
}
