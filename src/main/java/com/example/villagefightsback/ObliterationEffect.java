package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Obliteration": the mark an {@link ObliteratorItem} leaves on whatever it hits.
 *
 * <p>It does NOTHING on its own. No damage, no slowness, no wither. Its only job is to be visible and to be
 * present at the moment of death: a creature that dies carrying it is struck off the world's spawn tables by
 * the nearest burning Obliteration Furnace (see {@link ObliterationEvents}). Making it harmless is deliberate,
 * since the point of the weapon is what happens to the SPECIES, not to the individual, and stacking a damage
 * effect on top of a 14-damage sword would only blur which of the two did the killing.
 *
 * <p>Purple, and it shows its particles. Every other rule in this system is invisible bookkeeping -- what is in
 * which list, which furnace is nearest, how long the fuel lasts -- so the one part a player can actually see
 * has to carry the whole story: that creature is marked, and if it dies now it is gone for good.
 */
public class ObliterationEffect extends MobEffect {
    /** Amethyst violet, matching the Obliterator's blade and the furnace's flame. */
    private static final int COLOUR = 0xA855F7;

    public ObliterationEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
    }
}
