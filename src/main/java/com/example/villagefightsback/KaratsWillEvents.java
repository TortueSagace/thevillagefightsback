package com.example.villagefightsback;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the "Karat's Will" potion effect ({@link KaratsWillEffect}): while a creature holds it, it DEFLECTS the
 * whole attack -- combat damage is dealt to the attacker instead, and any harmful status effect an attacker
 * would inflict lands on the attacker instead. Also lets the priest (cleric) villager sell the potion.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KaratsWillEvents {
    private KaratsWillEvents() {}

    /**
     * Deflect combat damage onto the attacker. We cancel at HURT (not ATTACK) so the hit still "registers" --
     * on-hit effects (e.g. a wither skull's Wither) are still applied to the holder and then bounced to the
     * attacker by {@link #onEffectAdded}. The holder loses no health; the attacker takes the damage. Only damage
     * from a living attacker is deflected (fall / fire / drowning have no attacker and pass through normally).
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!victim.hasEffect(ModEffects.KARATS_WILL.get())) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
        float amount = event.getAmount();
        event.setCanceled(true);                          // the protected holder loses no health...
        if (amount > 0.0F) attacker.hurt(source, amount); // ...the attacker takes their own attack instead
    }

    /**
     * Deflect harmful status effects onto the attacker: a harmful effect a creature inflicts on a Karat's Will
     * holder is stripped from the holder and inflicted on the attacker instead (e.g. a wither's Wither lands on
     * the wither). Fired post-add, so we remove it from the holder and re-add it to the attacker. Re-adding with
     * NO source means it is never bounced again (and a holder's own beneficial Karat's Will is never reflected).
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.KARATS_WILL.get())) return;
        MobEffectInstance inst = event.getEffectInstance();
        MobEffect effect = inst.getEffect();
        if (effect.getCategory() != MobEffectCategory.HARMFUL) return; // only harmful effects are deflected
        if (!(event.getEffectSource() instanceof LivingEntity attacker) || attacker == target) return;
        MobEffectInstance copy = new MobEffectInstance(effect, inst.getDuration(), inst.getAmplifier(),
                inst.isAmbient(), inst.isVisible(), inst.showIcon());
        target.removeEffect(effect); // not on the protected holder...
        attacker.addEffect(copy);    // ...on the attacker instead (no source -> never re-bounced)
    }

    /** The priest (Cleric) sometimes sells the Karat's Will potion, at master level. */
    @SubscribeEvent
    public static void onClericTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CLERIC) return;
        ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION), ModPotions.KARATS_WILL.get());
        VillagerTrades.ItemListing listing = (trader, rand) ->
                new MerchantOffer(new ItemStack(Items.EMERALD, 18), potion.copy(), 3, 30, 0.05F);
        event.getTrades().get(5).add(listing); // 5 = master
    }
}
