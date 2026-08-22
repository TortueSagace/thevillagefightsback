package com.example.villagefightsback;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Mob-effect registration (the alien "Emerald Virus"). */
public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, TheVillageFightsBack.MODID);

    /** The contagious, irreversible "Emerald Virus" -- see {@link EmeraldVirusEffect} / {@link EmeraldVirus}. */
    public static final DeferredHolder<MobEffect, MobEffect> EMERALD_VIRUS =
            EFFECTS.register("emerald_virus", EmeraldVirusEffect::new);

    /** "Immunity": holder takes no damage while active (see {@link ImmunityEffect}). BrainNode aura + potion. */
    public static final DeferredHolder<MobEffect, MobEffect> IMMUNITY =
            EFFECTS.register("immunity", ImmunityEffect::new);

    /** "Emerald Diarrhea": the rare funny side-effect of eating Fertile Chorus (see {@link EmeraldDiarrheaEffect}). */
    public static final DeferredHolder<MobEffect, MobEffect> EMERALD_DIARRHEA =
            EFFECTS.register("emerald_diarrhea", EmeraldDiarrheaEffect::new);

    /** "Enemy of the Village": marks a player who attacked the village so defenders hunt them; 20 min, cleared
     *  on death, refreshed by re-offending. TWO TIERS: amplifier 0 is an ordinary enemy; amplifier 1 is what
     *  -500 reputation buys, and is what the sunset raid hunts (see {@link EnemyOfVillageEffect} /
     *  {@link ReputationData} / {@link NightmareRaid}). */
    public static final DeferredHolder<MobEffect, MobEffect> ENEMY_OF_THE_VILLAGE =
            EFFECTS.register("enemy_of_the_village", EnemyOfVillageEffect::new);

    /** "Nightmare scent": the mark the brain node puts on a player at sunset in Nightmare difficulty, and the
     *  thing a hunting raid follows. 10 min, uncurable by milk or death (see {@link NightmareScentEffect}). */
    public static final DeferredHolder<MobEffect, MobEffect> NIGHTMARE_SCENT =
            EFFECTS.register("nightmare_scent", NightmareScentEffect::new);

    /** "Very Bad Omen": gained after 30s straight in the Limbo; returning to a village unleashes a Limbo raid
     *  (see {@link VeryBadOmenEffect} / {@link LimboRaidManager}). */
    public static final DeferredHolder<MobEffect, MobEffect> VERY_BAD_OMEN =
            EFFECTS.register("very_bad_omen", VeryBadOmenEffect::new);

    /** "Darius' Special Sauce": deflects the whole attack -- combat damage and harmful effects an attacker inflicts on
     *  the holder hit the attacker instead (see {@link DariusSpecialEffect} / {@link DariusSpecialEffectEvents}). */
    public static final DeferredHolder<MobEffect, MobEffect> DARIUS_SPECIAL_EFFECT =
            EFFECTS.register("darius_special_effect", DariusSpecialEffect::new);

    /** "Emerald Marker": a brain node beacon that rallies every emerald unit within 100 blocks onto the marked
     *  target. Applied with VISIBLE particles, unlike the mod's other effects (see {@link EmeraldMarkerEffect}). */
    public static final DeferredHolder<MobEffect, MobEffect> EMERALD_MARKER =
            EFFECTS.register("emerald_marker", EmeraldMarkerEffect::new);

    /** What the Limbo's air does to anything that stays in it. See {@link SlowAnnihilationEffect}. */
    public static final DeferredHolder<MobEffect, MobEffect> SLOW_ANNIHILATION =
            EFFECTS.register("slow_annihilation", SlowAnnihilationEffect::new);

    /** "Obliteration": harmless in itself, but dying with it strikes the species off the spawn tables
     *  (see {@link ObliterationEffect} / {@link ObliterationEvents}). */
    public static final DeferredHolder<MobEffect, MobEffect> OBLITERATION =
            EFFECTS.register("obliteration", ObliterationEffect::new);

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
