package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Karat's Will": a beneficial effect that DEFLECTS the whole attack. While it is active (logic in
 * {@link KaratsWillEvents}), any combat damage an attacker would deal to the holder is dealt to the attacker
 * instead, and any harmful status effect an attacker would inflict on the holder is inflicted on the attacker
 * instead. Obtained as an orange potion (looted from our structure chests, or sold by the priest/cleric).
 *
 * Named in tribute to the Minecraft YouTuber Karat Feng. The colour is only the inventory/potion tint.
 */
public class KaratsWillEffect extends MobEffect {
    public KaratsWillEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE08020); // orange
    }
}
