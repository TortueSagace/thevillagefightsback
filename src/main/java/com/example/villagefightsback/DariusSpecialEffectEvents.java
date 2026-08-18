package com.example.villagefightsback;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * Drives the potion effect shown in game as "Darius' Special Sauce" / "Darius Special Power"
 * ({@link DariusSpecialEffect}). Registry id {@code darius_special_effect}: renamed from {@code karats_will}
 * after 1.5.1, which orphans any bottle saved by an earlier build.
 *
 * <p>While a creature holds it, it DEFLECTS the
 * whole attack -- combat damage is dealt to the attacker instead, and any harmful status effect an attacker
 * would inflict lands on the attacker instead. Also lets the priest (cleric) villager sell the potion.
 *
 * <p>Everything the WORLD does to the holder is reversed too, in {@link DariusSpecialEffectReversals}.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DariusSpecialEffectEvents {
    private DariusSpecialEffectEvents() {}

    /**
     * Deflect the WHOLE attack onto the attacker. We cancel at the earliest incoming-damage hook, so the holder
     * takes NO damage, NO knockback, and no invulnerability flicker, and the FULL incoming amount (before the
     * holder's own armour) is dealt straight back to the attacker. Only attacks from a living attacker are
     * deflected (fall / fire / drowning / cactus have no living attacker and pass through normally). Harmful
     * status effects are bounced separately by {@link #onEffectAdded}.
     */
    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!victim.hasEffect(ModEffects.DARIUS_SPECIAL_EFFECT)) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
        float amount = event.getAmount();
        event.setCanceled(true);                          // the protected holder takes nothing...
        if (amount <= 0.0F) return;
        if (victim instanceof ServerPlayer p) Achievements.award(p, "darius_special_effect/no_u");
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
     * Deflect harmful status effects onto the attacker: a harmful effect a creature inflicts on a Darius' Special Sauce
     * holder is stripped from the holder and inflicted on the attacker instead (e.g. a wither's Wither lands on
     * the wither). Fired post-add, so we remove it from the holder and re-add it to the attacker. Re-adding with
     * NO source means it is never bounced again (and a holder's own beneficial Darius' Special Sauce is never reflected).
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        MobEffectInstance inst = event.getEffectInstance();
        Holder<MobEffect> effect = inst.getEffect();

        // Our own effect landing: never deflected, just noted.
        //
        // Tested BEFORE the holder check, and that ordering is the whole fix. Forge posts this event from
        // addEffect BEFORE the instance is stored, so on a FIRST application hasEffect() is still false --
        // the old order bailed one line early and the root advancement could never be earned by drinking it.
        if (effect.value() == ModEffects.DARIUS_SPECIAL_EFFECT.value()) {   // our own effect landing: noted, never deflected
            if (target instanceof ServerPlayer p) Achievements.award(p, "darius_special_effect/root");
            return;
        }
        if (!target.hasEffect(ModEffects.DARIUS_SPECIAL_EFFECT)) return;
        if (effect.value().getCategory() != MobEffectCategory.HARMFUL) return; // only harmful effects are deflected
        if (!(event.getEffectSource() instanceof LivingEntity attacker) || attacker == target) return;
        MobEffectInstance copy = new MobEffectInstance(effect, inst.getDuration(), inst.getAmplifier(),
                inst.isAmbient(), inst.isVisible(), inst.showIcon());
        // ...and NOT on the holder. Removed TWICE on purpose, because the two loaders disagree about when
        // this event fires: Forge posts it from addEffect BEFORE the instance is stored, so removing it here
        // removed nothing and vanilla then added it anyway -- the "deflection" quietly handed the attacker a
        // copy while the holder kept the original. The immediate call covers the post-store ordering and the
        // queued one covers the pre-store ordering; removing an effect that is not there is a no-op, so doing
        // both is correct either way and does not depend on the loader.
        target.removeEffect(effect);
        net.minecraft.server.MinecraftServer server = target.level().getServer();
        if (server != null) server.execute(() -> target.removeEffect(effect));
        attacker.addEffect(copy);    // ...on the attacker instead (no source -> never re-bounced)
        if (target instanceof ServerPlayer p) Achievements.award(p, "darius_special_effect/own_medicine");
    }

    /** The priest (Cleric) sometimes sells the Darius' Special Sauce potion, at master level. */
    @SubscribeEvent
    public static void onClericTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CLERIC) return;
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(ModPotions.DARIUS_SPECIAL_EFFECT));
        VillagerTrades.ItemListing listing = (trader, rand) ->
                new MerchantOffer(new ItemCost(Items.EMERALD, 18), potion.copy(), 3, 30, 0.05F);
        event.getTrades().get(5).add(listing); // 5 = master
    }

    /** Cached handle to the protected {@code LivingEntity#die(DamageSource)}. */
    private static java.lang.reflect.Method dieMethod;

    /**
     * Kill {@code entity} through its real {@code die()} path so a boss with a custom death sequence (the
     * wither storm) plays its full death animation, rather than the silent {@code kill()} removal. {@code die}
     * is protected, so we invoke it reflectively (runtime uses Mojang official names). Health is zeroed
     * first so the dying state is consistent; falls back to {@code kill()} if reflection ever fails.
     */
    private static void killWithDeathAnimation(LivingEntity entity, DamageSource source) {
        try {
            if (dieMethod == null) {
                dieMethod = LivingEntity.class.getDeclaredMethod("die", DamageSource.class);
                dieMethod.setAccessible(true);
            }
            entity.setHealth(0.0F);
            dieMethod.invoke(entity, source);
        } catch (Throwable t) {
            entity.kill(); // last resort: clean removal
        }
    }
}
