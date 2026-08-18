package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Darius' Special Sauce": a beneficial effect that DEFLECTS the whole attack. While it is active (logic in
 * {@link DariusSpecialEffectEvents}), any combat damage an attacker would deal to the holder is dealt to the attacker
 * instead, and any harmful status effect an attacker would inflict on the holder is inflicted on the attacker
 * instead. Obtained as an orange potion (looted from our structure chests, or sold by the priest/cleric).
 *
 * Shown in game as "Darius Special Power"; the potion is "Darius' Special Sauce". It shipped through 1.5.1
 * as "Karat's Will", named in tribute to the Minecraft YouTuber Karat Feng, and was renamed along with its
 * registry id -- so a bottle saved by an older version no longer resolves. The colour is only the tint.
 */
public class DariusSpecialEffect extends MobEffect {
    public DariusSpecialEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE08020); // orange
    }
}
