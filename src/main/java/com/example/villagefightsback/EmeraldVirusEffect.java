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
 * Severity is set at INFECTION time by the amplifier (Virus II = Wither II, Virus I = Wither I + Slowness I).
 * Only a stage-4 village guard/villager imparts Virus II; everything else (vindictive boss minions,
 * contagion, the brain-node beam, ...) imparts Virus I -- regardless of how evolved the village is.
 */
public class EmeraldVirusEffect extends MobEffect {
    public EmeraldVirusEffect() {
        super(MobEffectCategory.HARMFUL, 0x2BBF4E);
    }

    /** Each second, refresh the (hidden) wither/slowness so they cover the whole virus length. */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level.isClientSide()) return;
        // Wither II only for Emerald Virus II (amplifier >= 1, i.e. a stage-4 guard/villager hit). The global
        // stage no longer auto-upgrades severity, so vindictive/contagion infections stay Virus I.
        if (amplifier >= 1) {
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
