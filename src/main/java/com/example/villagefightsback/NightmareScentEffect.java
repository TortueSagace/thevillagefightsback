package com.example.villagefightsback;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * "Nightmare scent": the mark the brain node puts on a player at sunset in Nightmare, and the thing the raid
 * is following.
 *
 * <p>Ten minutes of being hunted. It is not an attack in itself, so it does nothing per tick and deals no
 * damage; every consequence lives in {@code NightmareRaid}, which reads the mark and sends the village after
 * whoever wears it.
 *
 * <p>UNCURABLE, and the reason it needs its own effect rather than reusing Enemy of the Village. Enemy is a
 * standing, and can be earned and lost. This is a countdown that has started, and neither milk nor dying stops
 * it, so it carries an empty cure set filled here rather than per instance -- every grant of it should be
 * equally impossible to wash off, so the rule belongs on the effect.
 *
 * <p>The red glow the player sees is applied alongside it as vanilla GLOWING, which is what makes a hunted
 * player visible through walls to anyone watching, and which is itself the warning.
 */
public class NightmareScentEffect extends MobEffect {
    public NightmareScentEffect() {
        super(MobEffectCategory.HARMFUL, 0xC01414); // deep red: the HUD icon tint and the glow's colour
    }

    /**
     * Nothing happens per tick. The raid reads the mark; the mark itself is inert.
     *
     * <p>1.21 note: {@code isDurationEffectTick} was renamed to {@code shouldApplyEffectTickThisTick}. Same
     * question, same answer.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    /**
     * The empty cure set the class javadoc above promises: neither milk nor a totem lifts the scent.
     *
     * <p>1.21 note: on main this rule is written at the GRANT site instead, as
     * {@code inst.setCurativeItems(new ArrayList&lt;&gt;())} inside {@code NightmareRaid.mark}. NeoForge
     * replaced curative ITEMS with {@link EffectCure} tokens and moved the decision onto the effect, and the
     * default set that {@code IMobEffectExtension.fillEffectCures} would otherwise install is
     * {@code EffectCures.DEFAULT_CURES} = {MILK, PROTECTED_BY_TOTEM} -- so without this override a bucket of
     * milk or a Totem of Undying proc would wash the mark off and the raid would lose its scent. Leaving the
     * set empty is the behaviour-preserving equivalent, and it is strictly closer to what the javadoc always
     * said: the rule now genuinely lives on the effect and covers EVERY grant of it, not just the one call
     * site. Ported the same way as {@link EnemyOfVillageEffect} on this branch.
     */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
        // deliberately empty -- do NOT call super, which would add the default milk + totem cures
    }
}
