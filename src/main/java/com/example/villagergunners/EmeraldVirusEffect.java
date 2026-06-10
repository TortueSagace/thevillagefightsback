package com.example.villagergunners;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The "Emerald Virus": a harmful, contagious effect with NO particles of its own. While it is active it
 * inflicts Wither, and (driven elsewhere -- see {@link EmeraldVirus}) it tints the host progressively and
 * permanently green, spreads to nearby creatures, turns ground into dirt paths, and makes the host explode
 * shortly after death. The colour ({@code 0x2BBF4E}) is only used for the inventory-icon tint.
 */
public class EmeraldVirusEffect extends MobEffect {
    public EmeraldVirusEffect() {
        super(MobEffectCategory.HARMFUL, 0x2BBF4E);
    }

    /** Each second, refresh a (hidden) Wither so the virus withers the host for its whole length. */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, false, false, false));
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0; // once per second
    }
}
