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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
     * Deflect the WHOLE attack onto the attacker. We cancel at ATTACK (the earliest hook), so the holder takes
     * NO damage, NO knockback, and no invulnerability flicker, and the FULL incoming amount (before the holder's
     * own armour) is dealt straight back to the attacker. Only attacks from a living attacker are deflected
     * (fall / fire / drowning / cactus have no living attacker and pass through normally). Harmful status effects
     * are bounced separately by {@link #onEffectAdded}.
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!victim.hasEffect(ModEffects.KARATS_WILL.get())) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
        float amount = event.getAmount();
        event.setCanceled(true);                          // the protected holder takes nothing...
        if (amount <= 0.0F) return;
        // A wither storm's chomp is a Float.MAX_VALUE instakill, and its own invulnerability would just shrug
        // off the reflected damage -- so deflecting it back KILLS the storm instead, WITH its full death
        // sequence (death roar + sound + animation), not a silent removal.
        if (Factions.isWitherStorm(attacker) || Factions.isWitherStormPart(attacker)) {
            killWithDeathAnimation(attacker, source);
            return;
        }
        attacker.invulnerableTime = 0;                    // ...bypass i-frames so the deflected hit always lands...
        attacker.hurt(source, amount);                    // ...the attacker takes their own attack instead
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

    /** Cached handle to the protected {@code LivingEntity#die(DamageSource)} (SRG m_6667_). */
    private static java.lang.reflect.Method dieMethod;

    /**
     * Kill {@code entity} through its real {@code die()} path so a boss with a custom death sequence (the
     * wither storm) plays its full death animation, rather than the silent {@code kill()} removal. {@code die}
     * is protected, so we invoke it reflectively (Forge resolves the SRG name at runtime). Health is zeroed
     * first so the dying state is consistent; falls back to {@code kill()} if reflection ever fails.
     */
    private static void killWithDeathAnimation(LivingEntity entity, DamageSource source) {
        try {
            if (dieMethod == null) {
                dieMethod = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                        LivingEntity.class, "m_6667_", DamageSource.class);
            }
            entity.setHealth(0.0F);
            dieMethod.invoke(entity, source);
        } catch (Throwable t) {
            entity.kill(); // last resort: clean removal
        }
    }
}
