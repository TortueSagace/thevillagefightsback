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

    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
    }
}
