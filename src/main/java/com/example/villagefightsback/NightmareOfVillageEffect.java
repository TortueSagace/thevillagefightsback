package com.example.villagefightsback;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Nightmare of the Village": what the village calls you once your reputation reaches -500.
 *
 * <p>It does nothing on its own. Like {@link EnemyOfVillageEffect} it is a marker, and the only thing that
 * reads it is {@code NightmareRaid}: a player carrying it is hunted at sunset. It rides ON TOP of
 * {@link ModEffects#ENEMY_OF_THE_VILLAGE} rather than replacing it, which is the point -- every defender,
 * goal and air strike in the mod already asks the Enemy question, and none of them has to learn a second one
 * to keep working. Only the raid asks this.
 *
 * <p>Both are derived from the reputation number by {@link ReputationData}, and neither is ever granted by
 * anything else. Climb back above -500 and this lifts; climb above -100 and both do.
 */
public class NightmareOfVillageEffect extends MobEffect {
    /** Nearly black red. Enemy of the Village is 0x8B1A1A; this is the same colour taken further down. */
    private static final int COLOUR = 0x4A0000;

    public NightmareOfVillageEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
    }

    /**
     * A pure marker: nothing to do per tick, so never ask.
     *
     * <p>1.21 note: {@code isDurationEffectTick} was renamed to {@code shouldApplyEffectTickThisTick}. Same
     * question, same answer -- see {@link EnemyOfVillageEffect}, which this branch already ported the same way.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    /**
     * No cure exists for this status either: it lifts when the reputation number does, and by nothing else.
     *
     * <p>1.21 note: the same substitution {@link EnemyOfVillageEffect#fillEffectCures} documents. NeoForge has
     * no per-instance cure API, so main's {@code inst.setCurativeItems(new ArrayList<>())} has to live on the
     * effect. Without this override the default {MILK, PROTECTED_BY_TOTEM} set applies, and a bucket of milk
     * would clear the mark that says the village hunts you at every sunset -- while the reputation that
     * earned it sat untouched at -500, so the next re-derive would put it straight back. Flickering the worst
     * status in the mod on and off is worse than either state.
     */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
        // deliberately empty -- do NOT call super, which would add the default milk + totem cures
    }
}
