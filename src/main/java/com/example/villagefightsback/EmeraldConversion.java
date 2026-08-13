package com.example.villagefightsback;

import com.example.villagefightsback.infection.InfectionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * EMERALD faction combat rules + the emerald-unit death effects.
 *
 * <p><b>Faction sparing / friendly fire.</b> The village and the emerald faction never target or damage one
 * another, and emerald units never hit fellow emeralds. See {@link #onChangeTarget} / {@link #onFactionImmunity}
 * / {@link #onEmeraldHurt}.
 *
 * <p><b>Emerald death.</b> When an emerald unit dies, each infested path within 20 blocks erupts with an emerald
 * mite (globally rate-limited to 3 per 30s window), and every living emerald unit within 20 blocks gains Strength/
 * Resistance/Regeneration that ramps I -> II -> III on each successive nearby death (capped at III, duration refreshed).
 *
 * <p><b>No more conversion (1.1.4).</b> Emerald Virus replaced Emerald Infection: a mob that dies carrying the
 * virus spawns a mite (Virus I) or an Emeraldger (Virus II) instead of converting -- handled in
 * {@link EmeraldVirusEvents}, which also banks organic mass and plays the green death swirl.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldConversion {
    private EmeraldConversion() {}

    private static final double DEATH_RADIUS = 20.0;       // emerald-death effect reach
    private static final int BUFF_TICKS = 20 * 20;         // 20s death buff

    // Global rate limit on mites erupting from infested paths: at most MITE_BUDGET per tumbling MITE_WINDOW_TICKS
    // window (the counter resets to 0 at the start of each new window), across ALL emerald deaths.
    private static final int MITE_WINDOW_TICKS = 20 * 30;  // 30s window
    private static final int MITE_BUDGET = 3;              // <= 3 mites per window
    private static long miteWindowStart = Long.MIN_VALUE;
    private static int miteCount;

    /**
     * Faction sparing (v1.0.3): the VILLAGE faction and the Vindictive Village Spirit
     * ({@link UnleashedBrainNodeEntity}) never target emerald units (so air strikes, golems, guards, wall defenders
     * and Darius all leave them alone), and emerald units never target the village back (not even when provoked).
     * Other vindictive units (guards / gigachads) are deliberately NOT excluded -- they DO hunt emerald units.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target == null) return;
        LivingEntity me = event.getEntity();
        if (Factions.isEmerald(target) && (Factions.isVillage(me) || me instanceof UnleashedBrainNodeEntity)) {
            event.setCanceled(true);     // village + the Vindictive Village Spirit spare emerald units
            com.example.villagefightsback.compat.MCACompat.forgetEmeraldTarget(me); // and drop any brain-memory copy
        } else if (Factions.isEmerald(me) && (Factions.isVillage(target) || Factions.isEmerald(target))) {
            event.setCanceled(true);     // emerald never targets the village OR a fellow emerald, even when provoked
        }
    }

    /**
     * MCA Reborn only: when an emerald unit damages an MCA villager, call off the guards it just summoned.
     *
     * <p>{@code VillagerEntityMCA.hurt} broadcasts its attacker into the {@code ATTACK_TARGET} memory of every MCA
     * guard within 32 blocks using a RAW brain write, before it calls {@code super.hurt}. That happens ahead of
     * every Forge damage event, so neither the target-change cancel nor the faction damage-immunity below can
     * stop it: an emerald creeper blast or wither skull that merely clips a villager would set the whole guard
     * detail hunting a faction they are supposed to be at peace with.
     *
     * <p>{@link net.minecraftforge.event.entity.living.LivingAttackEvent} is the first thing Minecraft runs after
     * that broadcast, so this is the earliest possible undo. It is edge-triggered -- nothing runs unless an
     * emerald actually lands damage on an MCA villager.
     */
    @SubscribeEvent
    public static void onMcaVillagerHurtByEmerald(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!Factions.isEmerald(attacker)) return;
        // Only MCA's own villager class runs that broadcast, so anything else cannot have summoned guards.
        if (!com.example.villagefightsback.compat.MCACompat.isMcaVillager(event.getEntity())) return;
        com.example.villagefightsback.compat.MCACompat.recallGuardsHurtBy(event.getEntity());
    }

    /**
     * Counter-attack when provoked (rule 1) -- for ALL emerald units, including the Bat and Phantom which are plain
     * Mobs without a HurtByTargetGoal. An emerald unit retaliates against whoever hurt it, EXCEPT the village
     * faction and fellow emeralds (it never fights the village even when provoked; vindictive attackers DO get hit
     * back). Only sets a target when the unit is currently idle, so it doesn't thrash an existing target.
     */
    @SubscribeEvent
    public static void onEmeraldHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !Factions.isEmerald(mob)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !attacker.isAlive()) return;
        if (Factions.isEmerald(attacker) || Factions.isVillage(attacker)) return; // never retaliate vs emerald/village
        // NOTE: a player provoker IS retaliated against even when emerald_mobs_are_hostile_to_player is off -- "not
        // hostile" means neutral (don't start fights), not passive. The config only gates PROACTIVE player targeting
        // (in EmeraldMobs.isHostileTarget); a neutral emerald still fights back when the player strikes first.
        if (mob.getTarget() == null || !mob.getTarget().isAlive()) mob.setTarget(attacker);
    }

    /**
     * Track when an emerald unit DEALS damage to a valid target, so {@link OrganicMass}'s idle-reclaim timer can
     * cull (and refund) units that have been useless (e.g. stuck) for 5 minutes. Only relevant in organic mode.
     */
    @SubscribeEvent
    public static void onEmeraldDealtDamage(LivingHurtEvent event) {
        if (!Config.organicMassEnabled()) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker && Factions.isEmerald(attacker)
                && !Factions.isEmerald(event.getEntity()) && !Factions.isVillage(event.getEntity())) {
            OrganicMass.noteDamageDealt(attacker);
        }
    }

    /**
     * Emerald units are PASSIVE toward both the village AND each other: on top of never targeting one another
     * (see {@link #onChangeTarget}), neither can DAMAGE the other -- direct hits, projectiles (e.g. an emerald
     * wither's skull catching a fellow emerald), or blast splash are all cancelled. Highest priority + cancel,
     * so virus/effect handlers downstream are skipped too. Player and every other faction are unaffected, so the
     * player can still fight either side, and emeralds still hurt non-emerald/non-village mobs they hunt.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFactionImmunity(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        LivingEntity victim = event.getEntity();
        boolean aE = Factions.isEmerald(attacker), vE = Factions.isEmerald(victim);
        if ((aE && vE)                                        // emerald <-> emerald
                || (aE && Factions.isVillage(victim))         // emerald -> village
                || (Factions.isVillage(attacker) && vE)) {    // village -> emerald
            event.setCanceled(true);
            // MCA's guard villagers target through BRAIN MEMORY, which never reaches onChangeTarget above, so
            // without this they would chase an emerald unit forever, swinging for damage we keep cancelling.
            if (Factions.isVillage(attacker) && vE) {
                com.example.villagefightsback.compat.MCACompat.forgetEmeraldTarget(attacker);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level instanceof ServerLevel level)) return;
        if (!Config.mobInfectionEnabled()) return;                   // feature off -> no death effects
        // Only an EMERALD unit's death does anything here (mites + buff). A non-emerald mob that dies carrying the
        // virus is handled by EmeraldVirusEvents (mite/emeraldger spawn, organic mass, swirl, infested path).
        if (Factions.isEmerald(victim)) onEmeraldDeath(level, victim);
    }

    /** Infest the nearest solid block at/under the victim's feet, so a unit that died in MID-AIR leaves no
     *  floating infested path. Chests / too-tough blocks are rejected inside {@link InfectionManager#placeInfestedPath}. */
    static void placeInfestedPathUnder(ServerLevel level, LivingEntity victim) {
        net.minecraft.core.BlockPos.MutableBlockPos p = victim.blockPosition().mutable().move(0, -1, 0);
        for (int i = 0; i < 16 && level.isLoaded(p); i++) {
            net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.getMaterial().isReplaceable() && !s.getCollisionShape(level, p).isEmpty()) {
                InfectionManager.placeInfestedPath(level, p.immutable());
                return;
            }
            p.move(0, -1, 0);
        }
    }

    /** Spawn an emerald unit of {@code type} at {@code pos}, optionally placing {@code tool} in its main hand.
     *  No UNCAPPED_TAG, so the population cap applies (EmeraldPopulation absorbs/cancels it when at cap, and the
     *  enable_mob_infection master gate cancels it when the feature is off). */
    static void spawnAt(ServerLevel level, BlockPos pos, EntityType<?> type, ItemStack tool) {
        if (!Config.emeraldMobsEnabled()) return; // emerald faction disabled
        Entity e = type.create(level);
        if (!(e instanceof LivingEntity unit)) return;
        unit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (unit instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.CONVERSION, null, null);
        }
        if (tool != null) unit.setItemSlot(EquipmentSlot.MAINHAND, tool);
        level.addFreshEntity(unit);
    }

    // --- emerald-unit death effects ---------------------------------------------------------------
    private static void onEmeraldDeath(ServerLevel level, LivingEntity dead) {
        BlockPos center = dead.blockPosition();
        if (Config.organicMassEnabled()) OrganicMass.bumpThreatNearest(level, center); // an emerald died -> brain is under threat

        // 1. Each infested path within 20 blocks erupts with an emerald mite -- but globally rate-limited to
        //    MITE_BUDGET per 30s window (resets each window), so a mass AoE wipe near a dense infested field can't
        //    spawn (and amplify) a swarm.
        long now = level.getGameTime();
        if (now - miteWindowStart >= MITE_WINDOW_TICKS) { miteWindowStart = now; miteCount = 0; } // new window
        java.util.List<BlockPos> paths = InfectionManager.infestedPathsWithin(level, center, DEATH_RADIUS);
        int spawns = Math.max(0, Math.min(paths.size(), MITE_BUDGET - miteCount));
        for (int i = 0; i < spawns; i++) {
            spawnAt(level, paths.get(i).above(), ModEntities.EMERALD_ENDERMITE.get(), null);
        }
        miteCount += spawns;

        // 2. Every living emerald unit within 20 blocks gets a Str/Res/Regen buff that ramps I->II->III.
        buffNearbyEmeralds(level, center, dead);
    }

    /** Ramp Strength/Resistance/Regeneration (I -> II -> III, capped) on every live emerald within
     *  {@link #DEATH_RADIUS} of {@code center}, excluding {@code exclude} (may be null). Used by an emerald death
     *  and by the mite-cull that frees a cap slot (see {@link EmeraldPopulation}). */
    static void buffNearbyEmeralds(ServerLevel level, BlockPos center, LivingEntity exclude) {
        AABB box = new AABB(center).inflate(DEATH_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                u -> u.isAlive() && u != exclude && Factions.isEmerald(u))) {
            int cur = -1;
            MobEffectInstance ex = e.getEffect(MobEffects.DAMAGE_BOOST);
            if (ex != null) cur = ex.getAmplifier();
            int amp = Math.min(2, cur + 1);                          // bump one level, cap at III (amp 2)
            ramp(e, MobEffects.DAMAGE_BOOST, amp);
            ramp(e, MobEffects.DAMAGE_RESISTANCE, amp);
            ramp(e, MobEffects.REGENERATION, amp);
        }
    }

    private static void ramp(LivingEntity e, MobEffect eff, int amp) {
        e.addEffect(new MobEffectInstance(eff, BUFF_TICKS, amp, false, false, true)); // refreshes duration + level
    }
}
