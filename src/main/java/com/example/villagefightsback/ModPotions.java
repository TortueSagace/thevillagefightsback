package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Potion registration: the Immunity potion (a creative-tab bottle that grants the {@link ModEffects#IMMUNITY}
 *  effect). The effect itself is also grantable directly via {@code /effect give @s thevillagefightsback:immunity}. */
public final class ModPotions {
    private ModPotions() {}

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, TheVillageFightsBack.MODID);

    public static final int IMMUNITY_POTION_TICKS = 3 * 60 * 20; // 3 minutes

    public static final RegistryObject<Potion> IMMUNITY = POTIONS.register("immunity",
            () -> new Potion(new MobEffectInstance(ModEffects.IMMUNITY.get(), IMMUNITY_POTION_TICKS)));

    /** Potion of Emerald Virus -- brewed from awkward + an Emerald Apple. Splash / lingering / tipped arrows
     *  ("Arrow of Emerald Virus") all follow automatically from vanilla brewing. Inflicts Emerald Virus I. */
    public static final RegistryObject<Potion> EMERALD_VIRUS = POTIONS.register("emerald_virus",
            () -> new Potion(new MobEffectInstance(ModEffects.EMERALD_VIRUS.get(), EmeraldVirus.DURATION_TICKS)));

    /** Potion of Emerald Virus II -- the "strong" variant (Emerald Virus at amplifier 1 = Wither II). Brewed
     *  from a Potion of Emerald Virus + glowstone dust, mirroring vanilla strong-potion brewing. Like vanilla
     *  strong potions it runs for a shorter duration (half the base length). */
    public static final RegistryObject<Potion> EMERALD_VIRUS_STRONG = POTIONS.register("strong_emerald_virus",
            () -> new Potion(new MobEffectInstance(
                    ModEffects.EMERALD_VIRUS.get(), EmeraldVirus.DURATION_TICKS / 2, 1)));

    public static final int DARIUS_SPECIAL_EFFECT_TICKS = 10 * 20;       // base: 10 seconds
    public static final int DARIUS_SPECIAL_EFFECT_LONG_TICKS = 20 * 20;  // extended ("long"): 20 seconds

    /** Potion of Darius' Special Sauce: orange potion granting {@link ModEffects#DARIUS_SPECIAL_EFFECT}. The BASE potion is only
     *  looted / sold by the priest (no awkward-base brew); from it the standard brewing rules make the volatile
     *  (splash), lingering and long variants. Both share the name "darius_special_effect" so they read as one potion. */
    public static final RegistryObject<Potion> DARIUS_SPECIAL_EFFECT = POTIONS.register("darius_special_effect",
            () -> new Potion("darius_special_effect", new MobEffectInstance(ModEffects.DARIUS_SPECIAL_EFFECT.get(), DARIUS_SPECIAL_EFFECT_TICKS)));

    /** The "long" (extended-duration) Darius' Special Sauce potion -- brewed from the base potion + redstone. */
    public static final RegistryObject<Potion> DARIUS_SPECIAL_EFFECT_LONG = POTIONS.register("long_darius_special_effect",
            () -> new Potion("darius_special_effect", new MobEffectInstance(ModEffects.DARIUS_SPECIAL_EFFECT.get(), DARIUS_SPECIAL_EFFECT_LONG_TICKS)));

    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
    }
}
