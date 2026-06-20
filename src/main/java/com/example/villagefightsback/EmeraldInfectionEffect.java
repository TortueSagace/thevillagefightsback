package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Emerald Infection" (v1.0.3 mob infection): inflicted by ANY hit from an emerald-faction unit. It behaves like
 * Wither I -- a slow drain of 1 damage every 2 seconds -- but is applied with no particles (the instance is
 * created with visible=false). It is NOT the Emerald Virus. Its real purpose is the marker for conversion: a unit
 * that DIES while carrying it turns into an emerald unit ({@link EmeraldInfection}).
 */
public class EmeraldInfectionEffect extends MobEffect {
    public EmeraldInfectionEffect() {
        super(MobEffectCategory.HARMFUL, 0x4CE066); // emerald green tint (hidden while visible=false)
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().wither(), 1.0F); // Wither-style drain
    }

    /** Tick the drain like vanilla Wither: once every (40 >> amplifier) ticks. */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        int interval = 40 >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }
}
