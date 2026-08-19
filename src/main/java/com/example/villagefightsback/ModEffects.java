package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import com.example.villagefightsback.platform.registry.DeferredRegister;
import com.example.villagefightsback.platform.ModRegistries;
import com.example.villagefightsback.platform.registry.RegistryObject;

/** Mob-effect registration (the alien "Emerald Virus"). */
public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ModRegistries.MOB_EFFECTS, TheVillageFightsBack.MODID);

    /** The contagious, irreversible "Emerald Virus" -- see {@link EmeraldVirusEffect} / {@link EmeraldVirus}. */
    public static final RegistryObject<MobEffect> EMERALD_VIRUS =
            EFFECTS.register("emerald_virus", EmeraldVirusEffect::new);

    /** "Immunity": holder takes no damage while active (see {@link ImmunityEffect}). BrainNode aura + potion. */
    public static final RegistryObject<MobEffect> IMMUNITY =
            EFFECTS.register("immunity", ImmunityEffect::new);

    /** "Emerald Diarrhea": the rare funny side-effect of eating Fertile Chorus (see {@link EmeraldDiarrheaEffect}). */
    public static final RegistryObject<MobEffect> EMERALD_DIARRHEA =
            EFFECTS.register("emerald_diarrhea", EmeraldDiarrheaEffect::new);

    /** "Enemy of the Village": the village hunts a player carrying this. DERIVED from reputation -- it lands at
     *  -100 and lifts above it, and nothing else grants it. TWO TIERS: amplifier 0 is an ordinary enemy;
     *  amplifier 1 is what -500 buys, and is what the sunset raid hunts (see {@link EnemyOfVillageEffect} /
     *  {@link ReputationData} / {@link NightmareRaid}). */
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

    /** "Darius' Special Sauce": deflects the whole attack -- combat damage and harmful effects an attacker inflicts on
     *  the holder hit the attacker instead (see {@link DariusSpecialEffect} / {@link DariusSpecialEffectEvents}). */
    public static final RegistryObject<MobEffect> DARIUS_SPECIAL_EFFECT =
            EFFECTS.register("darius_special_effect", DariusSpecialEffect::new);

    /** "Emerald Marker": a brain node beacon that rallies every emerald unit within 100 blocks onto the marked
     *  target. Applied with VISIBLE particles, unlike the mod's other effects (see {@link EmeraldMarkerEffect}). */
    public static final RegistryObject<MobEffect> EMERALD_MARKER =
            EFFECTS.register("emerald_marker", EmeraldMarkerEffect::new);

    public static void register() {
        EFFECTS.register();
    }
}
