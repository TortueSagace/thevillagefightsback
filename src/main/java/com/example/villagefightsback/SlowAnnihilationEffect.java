package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Slow Annihilation": what the Limbo's air does to anything that stays in it.
 *
 * <p>Ten seconds in and it starts, and it keeps being renewed for as long as you are still there (see
 * {@link SlowAnnihilationEvents}). It is Wither and Levitation together, which is a deliberately nasty pair:
 * the Wither is what kills you and the Levitation is what stops you doing anything about it, because you
 * cannot walk to the bed while you are drifting off the floor.
 *
 * <p>A player can still fight it -- crouching cancels the rise and drops you at the speed you were going up,
 * so the exit is reachable if you know that. A Gas Mask stops it happening at all.
 *
 * <p>The effects are re-applied hidden ({@code false, false, false}) exactly as {@link EmeraldVirusEffect}
 * does, so the HUD shows one icon -- this one -- rather than three competing ones, and the player reads a
 * single cause instead of a pile of symptoms.
 */
public class SlowAnnihilationEffect extends MobEffect {
    /** How long the borrowed effects are topped up for, in ticks. Longer than the 1s tick so they never gap. */
    private static final int TOPUP = 40;

    public SlowAnnihilationEffect() {
        // The Forgotten Powder green, which is what the icon is cut from.
        super(MobEffectCategory.HARMFUL, 0x6BBF3A);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return true;
        entity.addEffect(new MobEffectInstance(MobEffects.WITHER, TOPUP, 0, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, TOPUP, 0, false, false, false));
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;   // once a second is enough to keep a 40-tick top-up alive
    }
}
