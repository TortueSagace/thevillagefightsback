package com.example.villagefightsback;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Potion registration: the Immunity potion (a creative-tab bottle that grants the {@link ModEffects#IMMUNITY}
 *  effect). The effect itself is also grantable directly via {@code /effect give @s thevillagefightsback:immunity}. */
public final class ModPotions {
    private ModPotions() {}

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(BuiltInRegistries.POTION, TheVillageFightsBack.MODID);

    public static final int IMMUNITY_POTION_TICKS = 3 * 60 * 20; // 3 minutes

    public static final DeferredHolder<Potion, Potion> IMMUNITY = POTIONS.register("immunity",
            () -> new Potion("immunity", new MobEffectInstance(ModEffects.IMMUNITY, IMMUNITY_POTION_TICKS)));

    /** Potion of Emerald Virus -- brewed from awkward + an Emerald Apple. Splash / lingering / tipped arrows
     *  ("Arrow of Emerald Virus") all follow automatically from vanilla brewing. Inflicts Emerald Virus I. */
    public static final DeferredHolder<Potion, Potion> EMERALD_VIRUS = POTIONS.register("emerald_virus",
            () -> new Potion("emerald_virus", new MobEffectInstance(ModEffects.EMERALD_VIRUS, EmeraldVirus.DURATION_TICKS)));

    /** Potion of Emerald Virus II -- the "strong" variant (Emerald Virus at amplifier 1 = Wither II). Brewed
     *  from a Potion of Emerald Virus + glowstone dust, mirroring vanilla strong-potion brewing. Like vanilla
     *  strong potions it runs for a shorter duration (half the base length). */
    public static final DeferredHolder<Potion, Potion> EMERALD_VIRUS_STRONG = POTIONS.register("strong_emerald_virus",
            () -> new Potion("emerald_virus", new MobEffectInstance(
                    ModEffects.EMERALD_VIRUS, EmeraldVirus.DURATION_TICKS / 2, 1)));

    public static final int KARATS_WILL_TICKS = 10 * 20;       // base: 10 seconds
    public static final int KARATS_WILL_LONG_TICKS = 20 * 20;  // extended ("long"): 20 seconds

    /** Potion of Karat's Will: orange potion granting {@link ModEffects#KARATS_WILL}. The BASE potion is only
     *  looted / sold by the priest (no awkward-base brew); from it the standard brewing rules make the volatile
     *  (splash), lingering and long variants. Both share the name "karats_will" so they read as one potion. */
    public static final DeferredHolder<Potion, Potion> KARATS_WILL = POTIONS.register("karats_will",
            () -> new Potion("karats_will", new MobEffectInstance(ModEffects.KARATS_WILL, KARATS_WILL_TICKS)));

    /** The "long" (extended-duration) Karat's Will potion -- brewed from the base potion + redstone. */
    public static final DeferredHolder<Potion, Potion> KARATS_WILL_LONG = POTIONS.register("long_karats_will",
            () -> new Potion("karats_will", new MobEffectInstance(ModEffects.KARATS_WILL, KARATS_WILL_LONG_TICKS)));

    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
    }
}
