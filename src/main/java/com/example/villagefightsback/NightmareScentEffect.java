package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

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

    /** Nothing happens per tick. The raid reads the mark; the mark itself is inert. */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
}
