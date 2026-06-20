package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the EMERALD INFECTION (v1.0.3 "mob infection"). ANY hit from an emerald-faction unit inflicts the
 * {@link ModEffects#EMERALD_INFECTION} effect for 20s -- and because we key off the damage source's RESPONSIBLE
 * entity ({@link net.minecraft.world.damagesource.DamageSource#getEntity()}), this single check covers melee
 * hits, skeleton arrows, the creeper's explosion, and the wither's skulls (all attribute back to the emerald
 * unit). Conversion of a unit that DIES while infected (or is one-shot by an emerald unit) is handled separately.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldInfection {
    private EmeraldInfection() {}

    public static final int INFECT_TICKS = 20 * 20; // 20 seconds

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!Config.mobInfectionEnabled()) return;  // mob infection disabled -> never infect anything
        Entity src = event.getSource().getEntity(); // attacker / arrow shooter / creeper (explosion) / wither (skull)
        if (!Factions.isEmerald(src)) return;
        LivingEntity victim = event.getEntity();
        // Spared factions are never infected (so they never convert): fellow emeralds, the village, the vindictive.
        if (Factions.isEmerald(victim) || Factions.isVillage(victim) || Factions.isInfected(victim)) return;
        apply(victim);
    }

    /**
     * Master gate for "enable_mob_infection": when the feature is OFF, NO emerald unit may ever enter the world,
     * via ANY path (infection-conversion, brain summon, or even a creative spawn egg). Cheap when ON (a single
     * boolean check, the default), so it only inspects entities when the feature is disabled.
     */
    @SubscribeEvent
    public static void onEmeraldJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (Config.mobInfectionEnabled() || event.getLevel().isClientSide()) return;
        if (Factions.isEmerald(event.getEntity())) event.setCanceled(true);
    }

    /** Inflict Emerald Infection (Wither I, no particles) for 20 seconds. */
    public static void apply(LivingEntity victim) {
        victim.addEffect(new MobEffectInstance(ModEffects.EMERALD_INFECTION.get(), INFECT_TICKS, 0, false, false, true));
    }
}
