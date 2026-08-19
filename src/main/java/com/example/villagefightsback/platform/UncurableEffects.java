package com.example.villagefightsback.platform;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Status effects that milk and a totem do NOT lift.
 *
 * <p>Forge lets each effect instance carry its own list of curative items, and the mod hands two of them an empty
 * one -- the nightmare-raid scent and the village-standing effect are not poisons and are not meant to be
 * drinkable away. Vanilla has no such notion: milk calls {@code removeAllEffects} and takes everything.
 *
 * <p>So the effects are recorded here and a mixin on {@code LivingEntity.removeAllEffects} puts them back. The
 * granularity is coarser than Forge's -- an effect is uncurable, rather than one instance of it -- which makes no
 * difference for these two, since every instance the mod creates is marked.
 */
public final class UncurableEffects {
    private UncurableEffects() {}

    private static final Set<MobEffect> UNCURABLE =
            Collections.newSetFromMap(new WeakHashMap<>());

    /** Marks this instance's effect as one that milk and a totem leave alone. */
    public static void mark(MobEffectInstance instance) {
        UNCURABLE.add(instance.getEffect());
    }

    public static boolean isUncurable(MobEffect effect) {
        return UNCURABLE.contains(effect);
    }
}
