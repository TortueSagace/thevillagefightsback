package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Very Bad Omen": gained after spending more than 30 seconds straight in the Limbo. It does nothing on its
 * own -- it is a flag. When a player carrying it returns to a village, {@link LimboRaidManager} consumes it and
 * unleashes a Limbo raid. Applied with no particles (see {@link LimboRaidManager#applyOmen}).
 */
public class VeryBadOmenEffect extends MobEffect {
    public VeryBadOmenEffect() {
        super(MobEffectCategory.NEUTRAL, 0x0E2A18); // dark limbo green
    }

    /** Never run an effect tick -- this effect is purely a marker the raid manager reads. */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
