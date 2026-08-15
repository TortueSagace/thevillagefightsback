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

    /** "Nightmare scent": the mark the brain node puts on a player at sunset in Nightmare difficulty, and the
     *  thing a hunting raid follows. 10 min, uncurable by milk or death (see {@link NightmareScentEffect}). */
    public static final RegistryObject<MobEffect> NIGHTMARE_SCENT =
            EFFECTS.register("nightmare_scent", NightmareScentEffect::new);

    /** "Obliteration": harmless in itself, but dying with it strikes the species off the spawn tables
     *  (see {@link ObliterationEffect} / {@link ObliterationEvents}). */
    public static final RegistryObject<MobEffect> OBLITERATION =
            EFFECTS.register("obliteration", ObliterationEffect::new);

    /** "Very Bad Omen": gained after 30s straight in the Limbo; returning to a village unleashes a Limbo raid
     *  (see {@link VeryBadOmenEffect} / {@link LimboRaidManager}). */
    public static final RegistryObject<MobEffect> VERY_BAD_OMEN =
            EFFECTS.register("very_bad_omen", VeryBadOmenEffect::new);

    /** "Karat's Will": deflects the whole attack -- combat damage and harmful effects an attacker inflicts on
     *  the holder hit the attacker instead (see {@link KaratsWillEffect} / {@link KaratsWillEvents}). */
    public static final RegistryObject<MobEffect> KARATS_WILL =
            EFFECTS.register("karats_will", KaratsWillEffect::new);

    /** "Emerald Marker": a brain node beacon that rallies every emerald unit within 100 blocks onto the marked
     *  target. Applied with VISIBLE particles, unlike the mod's other effects (see {@link EmeraldMarkerEffect}). */
    public static final RegistryObject<MobEffect> EMERALD_MARKER =
            EFFECTS.register("emerald_marker", EmeraldMarkerEffect::new);

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
