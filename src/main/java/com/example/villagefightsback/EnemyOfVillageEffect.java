package com.example.villagefightsback;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * "Enemy of the Village": a marker status placed on a player who attacked the village -- by mining a Village
 * Brain Node / Emerald Node, or by harming a villager / guard / iron golem. While a player holds it, every
 * nearby defender hunts them on sight (see {@link VillageEvents#getAggroPlayer}). It lasts 20 minutes and is
 * cleared on death; re-offending refreshes the timer. It has no per-tick behaviour of its own and is applied
 * with particles off (icon only -- the bad-omen icon).
 *
 * <p>It is also UNCURABLE -- see {@link #fillEffectCures}.
 */
public class EnemyOfVillageEffect extends MobEffect {
    public EnemyOfVillageEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8B1A1A); // dark red (colour is unused while particles are off)
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false; // purely a marker -- never runs applyEffectTick
    }

    /**
     * No cure exists for this status: it runs its 20 minutes (1 min for theft) or ends with the player.
     *
     * <p>1.21 note: this replaces main's per-grant {@code inst.setCurativeItems(new ArrayList&lt;&gt;())}.
     * NeoForge moved curing to {@link EffectCure} tokens, and the DEFAULT set that
     * {@code IMobEffectExtension.fillEffectCures} would otherwise install is
     * {@code EffectCures.DEFAULT_CURES} = {MILK, PROTECTED_BY_TOTEM} -- so without this override a bucket of
     * milk ({@code MilkBucketItem.finishUsingItem} -> {@code removeEffectsCuredBy(MILK)}) or a Totem of Undying
     * proc ({@code LivingEntity} -> {@code removeEffectsCuredBy(PROTECTED_BY_TOTEM)}) would wipe the mod's core
     * punishment, and the hunt with it. Leaving the set empty is the behaviour-preserving equivalent: the
     * instance's cure set is filled from here by the {@code MobEffectInstance} constructor, an empty set
     * round-trips through save/load as empty, and it covers EVERY grant of this effect rather than the
     * individual call sites.
     */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
        // deliberately empty -- do NOT call super, which would add the default milk + totem cures
    }
}
