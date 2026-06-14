package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The "Emerald Virus": a harmful, contagious effect with NO particles of its own. While it is active it
 * withers the host, and (driven elsewhere -- see {@link EmeraldVirus}) it tints the host progressively and
 * permanently green, spreads to nearby creatures, turns ground into dirt paths, and makes the host explode
 * shortly after death. The colour ({@code 0x2BBF4E}) is only used for the inventory-icon tint.
 *
 * The virus EVOLVES once any Village Brain Node reaches level 4 ({@link EmeraldVirus#isEvolved}): the host then
 * suffers Wither II instead of the base Wither I + Slowness I.
 */
public class EmeraldVirusEffect extends MobEffect {
    public EmeraldVirusEffect() {
        super(MobEffectCategory.HARMFUL, 0x2BBF4E);
    }

    /** Each second, refresh the (hidden) wither/slowness so they cover the whole virus length. */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;
        // Evolved (Wither II) if this is Emerald Virus II (a stage-4 attacker's hit) OR any brainNode has hit level 4.
        if (amplifier >= 1 || EmeraldVirus.isEvolved(entity.getServer())) {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 1, false, false, false)); // Wither II
        } else {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, false, false, false));          // Wither I
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, false)); // Slowness I
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0; // once per second
    }
}
