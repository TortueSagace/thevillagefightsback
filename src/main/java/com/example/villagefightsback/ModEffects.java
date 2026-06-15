package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Mob-effect registration (the alien "Emerald Virus"). */
public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TheVillageFightsBack.MODID);

    /** The contagious, irreversible "Emerald Virus" -- see {@link EmeraldVirusEffect} / {@link EmeraldVirus}. */
    public static final RegistryObject<MobEffect> EMERALD_VIRUS =
            EFFECTS.register("emerald_virus", EmeraldVirusEffect::new);

    /** "Immunity": holder takes no damage while active (see {@link ImmunityEffect}). BrainNode aura + potion. */
    public static final RegistryObject<MobEffect> IMMUNITY =
            EFFECTS.register("immunity", ImmunityEffect::new);

    /** "Emerald Diarrhea": the rare funny side-effect of eating Fertile Chorus (see {@link EmeraldDiarrheaEffect}). */
    public static final RegistryObject<MobEffect> EMERALD_DIARRHEA =
            EFFECTS.register("emerald_diarrhea", EmeraldDiarrheaEffect::new);

    /** "Enemy of the Village": marks a player who attacked the village so defenders hunt them; 20 min, cleared
     *  on death, refreshed by re-offending (see {@link EnemyOfVillageEffect} / {@link VillageEvents}). */
    public static final RegistryObject<MobEffect> ENEMY_OF_THE_VILLAGE =
            EFFECTS.register("enemy_of_the_village", EnemyOfVillageEffect::new);

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
