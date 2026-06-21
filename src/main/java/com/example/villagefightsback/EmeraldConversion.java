package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.example.villagefightsback.infection.InfectionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * EMERALD CONVERSION + the emerald-unit death effects (v1.0.3 "mob infection").
 *
 * <p><b>Conversion.</b> A non-emerald unit that DIES while carrying {@link ModEffects#EMERALD_INFECTION} -- or is
 * killed outright by an emerald unit -- is replaced by an emerald unit: its direct counterpart (cow -> emerald cow)
 * if it has one, else the emerald unit whose representative max HP is closest. Some HP slots are CLUSTERS of more
 * than one type and resolve to a random member (the 10-HP cluster -> cow/horse, the 20-HP cluster -> skeleton/
 * phantom). The replacement keeps the victim's held tool. A lime banishment swirl plays, the block right below
 * becomes an infested path, and every brain node whose radius the victim died in earns +1 emerald. A PLAYER who
 * dies infected does NOT convert -- an emerald mob spawns in their place instead. Spared factions (village,
 * vindictive, fellow emeralds) never convert.
 *
 * <p><b>Emerald death.</b> When an emerald unit dies, each infested path within 20 blocks erupts with an emerald
 * mite (globally rate-limited to 3 per 30s window), and every living emerald unit within 20 blocks gains Strength/
 * Resistance/Regeneration that ramps I -> II -> III on each successive nearby death (capped at III, duration refreshed).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldConversion {
    private EmeraldConversion() {}

    private static final double DEATH_RADIUS = 20.0;       // emerald-death effect reach (spec 8)
    private static final int BUFF_TICKS = 20 * 20;         // 20s death buff

    // Global rate limit on mites erupting from infested paths: at most MITE_BUDGET per tumbling MITE_WINDOW_TICKS
    // window (the counter resets to 0 at the start of each new window), across ALL emerald deaths.
    private static final int MITE_WINDOW_TICKS = 20 * 30;  // 30s window
    private static final int MITE_BUDGET = 3;              // <= 3 mites per window
    private static long miteWindowStart = Long.MIN_VALUE;
    private static int miteCount;

    // --- direct correspondence (vanilla type -> emerald type), built lazily after registration ---
    private static Map<EntityType<?>, EntityType<?>> direct;

    private static void initDirect() {
        if (direct != null) return;
        Map<EntityType<?>, EntityType<?>> m = new HashMap<>();
        m.put(EntityType.SHEEP, ModEntities.RICH_SHEEP.get());       // the emerald sheep (kept registry id rich_sheep)
        m.put(EntityType.COW, ModEntities.EMERALD_COW.get());
        m.put(EntityType.CHICKEN, ModEntities.EMERALD_CHICKEN.get());
        m.put(EntityType.CAT, ModEntities.EMERALD_CAT.get());
        m.put(EntityType.ENDERMITE, ModEntities.EMERALD_ENDERMITE.get());
        m.put(EntityType.WOLF, ModEntities.EMERALD_WOLF.get());
        m.put(EntityType.HORSE, ModEntities.EMERALD_HORSE.get());
        m.put(EntityType.PIG, ModEntities.EMERALD_PIG.get());
        m.put(EntityType.SALMON, ModEntities.EMERALD_SALMON.get());
        m.put(EntityType.BAT, ModEntities.EMERALD_BAT.get());
        m.put(EntityType.ZOMBIE, ModEntities.EMERALD_ZOMBIE.get());
        m.put(EntityType.SKELETON, ModEntities.EMERALD_SKELETON.get());
        m.put(EntityType.CREEPER, ModEntities.EMERALD_CREEPER.get());
        m.put(EntityType.ENDERMAN, ModEntities.EMERALD_ENDERMAN.get());
        m.put(EntityType.IRON_GOLEM, ModEntities.EMERALD_IRON_GOLEM.get());
        m.put(EntityType.PHANTOM, ModEntities.EMERALD_PHANTOM.get());
        m.put(EntityType.WITHER, ModEntities.EMERALD_WITHER.get());
        m.put(EntityType.GUARDIAN, ModEntities.EMERALD_GUARDIAN.get());
        m.put(EntityType.ELDER_GUARDIAN, ModEntities.EMERALD_ELDER_GUARDIAN.get());
        direct = m;
    }

    // --- closest-HP fallback roster: each representative max HP maps to a CLUSTER of one or more emerald types.
    //     When a cluster holds several types, the converted unit is a RANDOM one of them (e.g. the 10-HP cluster
    //     resolves 50/50 to cow/horse). Clusters are AQUATIC or TERRESTRIAL: an aquatic victim draws ONLY from the
    //     aquatic clusters (salmon / guardian / elder guardian), a land victim only from the rest. ---
    private record HpEntry(double hp, boolean aquatic, EntityType<?>[] types) {}
    private static HpEntry[] roster;

    /** A TERRESTRIAL cluster -- candidates for a land victim only. */
    private static HpEntry cluster(double hp, EntityType<?>... types) {
        return new HpEntry(hp, false, types);
    }

    /** An AQUATIC cluster (salmon / guardian / elder guardian) -- candidates for a water victim only. */
    private static HpEntry aquaticCluster(double hp, EntityType<?>... types) {
        return new HpEntry(hp, true, types);
    }

    private static void initRoster() {
        if (roster != null) return;
        roster = new HpEntry[]{
                aquaticCluster(3, ModEntities.EMERALD_SALMON.get()),
                cluster(4, ModEntities.EMERALD_CHICKEN.get()),
                cluster(6, ModEntities.EMERALD_BAT.get()),
                cluster(8, ModEntities.EMERALD_WOLF.get()),
                // 10-HP cluster: cow OR horse. The horse lives HERE (not on its own 15-HP slot) so the very common
                // ~15-HP mobs (spiders are 16) no longer ALL deterministically become horses.
                cluster(10, ModEntities.EMERALD_COW.get(), ModEntities.EMERALD_HORSE.get()),
                cluster(20, ModEntities.EMERALD_SKELETON.get()), // 20-HP -> skeleton (phantom only via direct conversion)
                aquaticCluster(30, ModEntities.EMERALD_GUARDIAN.get()),
                cluster(40, ModEntities.EMERALD_ENDERMAN.get()),
                aquaticCluster(80, ModEntities.EMERALD_ELDER_GUARDIAN.get()),
                cluster(100, ModEntities.EMERALD_IRON_GOLEM.get()),
                cluster(300, ModEntities.EMERALD_WITHER.get()),
        };
    }

    /** The emerald type a dying unit converts into: direct counterpart, else closest max HP within its element. */
    private static EntityType<?> pick(LivingEntity victim, RandomSource rnd) {
        initDirect();
        EntityType<?> d = direct.get(victim.getType());
        if (d != null) return d;
        return pickByHp(victim.getMaxHealth(), rnd, isAquatic(victim));
    }

    /** Aquatic victims convert only into salmon/guardian/elder guardian; everyone else only into land units. */
    private static boolean isAquatic(LivingEntity e) {
        return e.getMobType() == net.minecraft.world.entity.MobType.WATER
                || e instanceof net.minecraft.world.entity.animal.WaterAnimal;
    }

    private static EntityType<?> pickByHp(double hp, RandomSource rnd, boolean aquatic) {
        initRoster();
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < roster.length; i++) {
            // A fish never becomes a cow, and a cow never becomes a fish: only consider this victim's element.
            if (roster[i].aquatic() != aquatic) continue;
            double d = Math.abs(roster[i].hp - hp);
            if (d < bestD) { bestD = d; best = i; }
        }
        EntityType<?>[] types = roster[best].types();          // a same-HP cluster -> pick a random member
        return types.length == 1 ? types[0] : types[rnd.nextInt(types.length)];
    }

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
        } else if (Factions.isEmerald(me) && (Factions.isVillage(target) || Factions.isEmerald(target))) {
            event.setCanceled(true);     // emerald never targets the village OR a fellow emerald, even when provoked
        }
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
        }
    }

    /**
     * True if {@code victim} will be REPLACED by an emerald unit when it dies (the conversion gate used by
     * {@link #onDeath}). Such a unit drops NO loot -- it is consumed by the conversion (its held tool transfers
     * to the replacement). An ALREADY-emerald unit returns false here, so emerald units that die still drop
     * their normal loot; players also return false (they don't convert).
     */
    static boolean convertsOnDeath(LivingEntity victim, DamageSource source) {
        if (Config.organicMassEnabled()) return false; // organic mode: nothing converts, so loot is never suppressed
        if (!Config.mobInfectionEnabled() || victim instanceof Player) return false;
        if (Factions.isEmerald(victim) || Factions.isVillage(victim) || Factions.isInfected(victim)) return false;
        if (victim instanceof net.minecraft.world.entity.monster.Slime) return false; // slimes/magma cubes never convert
        return victim.hasEffect(ModEffects.EMERALD_INFECTION.get()) || Factions.isEmerald(source.getEntity());
    }

    /** A non-emerald unit that is converted into an emerald on death drops no loot. */
    @SubscribeEvent
    public static void onConvertedDrops(LivingDropsEvent event) {
        if (convertsOnDeath(event.getEntity(), event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (!Config.mobInfectionEnabled()) return;                   // feature off -> no conversion, no death effects

        if (Factions.isEmerald(victim)) { onEmeraldDeath(level, victim); return; }
        if (Factions.isVillage(victim) || Factions.isInfected(victim)) return; // spared factions never convert
        if (victim instanceof net.minecraft.world.entity.monster.Slime) return; // slimes/magma cubes: targets, never convert

        boolean killedByEmerald = Factions.isEmerald(event.getSource().getEntity());
        if (Config.organicMassEnabled()) {
            // ORGANIC MASS mode: an emerald unit's kill does NOT convert the victim -- it lays infested ground and
            // banks the victim's max HP as organic mass on the nearest brain (spent later by OrganicMass). Loot
            // drops normally; player-/environment-caused deaths do nothing; no emerald spawns from a death here.
            if (killedByEmerald) {
                placeInfestedPathUnder(level, victim);
                OrganicMass.addToNearestBrain(level, victim.blockPosition(), Math.round(victim.getMaxHealth()));
            }
            return;
        }

        boolean infected = victim.hasEffect(ModEffects.EMERALD_INFECTION.get());
        if (!infected && !killedByEmerald) return;
        // The brain is fed by INFECTION deaths only -- an emerald unit's OWN kills don't feed it (too OP a loop).
        boolean feedBrain = !killedByEmerald;

        if (victim instanceof Player) {
            // A player doesn't turn -- an emerald mob spawns in their place (closest to the player's max HP).
            spawnAt(level, victim.blockPosition(), pickByHp(victim.getMaxHealth(), level.getRandom(), false), null);
            LimboBanishFx.startLime(victim);
            if (feedBrain) grantEmeraldsToBrains(level, victim.blockPosition());
            placeInfestedPathUnder(level, victim);
            return;
        }
        convert(level, victim, feedBrain);
    }

    /** Replace the dying {@code victim} with an emerald unit at the same spot (keeping its held tool). */
    private static void convert(ServerLevel level, LivingEntity victim, boolean feedBrain) {
        EntityType<?> type = pick(victim, level.getRandom());
        LimboBanishFx.startLime(victim);                              // lime banishment swirl on the original
        ItemStack held = victim.getMainHandItem();
        spawnAt(level, victim.blockPosition(), type, held.isEmpty() ? null : held.copy());
        if (feedBrain) grantEmeraldsToBrains(level, victim.blockPosition());
        placeInfestedPathUnder(level, victim);
    }

    /** Infest the nearest solid block at/under the victim's feet, so a unit that died in MID-AIR leaves no
     *  floating infested path. Chests / too-tough blocks are rejected inside {@link InfectionManager#placeInfestedPath}. */
    private static void placeInfestedPathUnder(ServerLevel level, LivingEntity victim) {
        net.minecraft.core.BlockPos.MutableBlockPos p = victim.blockPosition().mutable().move(0, -1, 0);
        for (int i = 0; i < 16 && level.isLoaded(p); i++) {
            net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.canBeReplaced() && !s.getCollisionShape(level, p).isEmpty()) {
                InfectionManager.placeInfestedPath(level, p.immutable());
                return;
            }
            p.move(0, -1, 0);
        }
    }

    /** Spawn an emerald unit of {@code type} at {@code pos}, optionally placing {@code tool} in its main hand. */
    private static void spawnAt(ServerLevel level, BlockPos pos, EntityType<?> type, ItemStack tool) {
        Entity e = type.create(level);
        if (!(e instanceof LivingEntity unit)) return;
        unit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (unit instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.CONVERSION, null, null);
        }
        if (tool != null) unit.setItemSlot(EquipmentSlot.MAINHAND, tool);
        level.addFreshEntity(unit);                                  // (auto-cancelled by EmeraldInfection if feature off)
    }

    /** +1 emerald to every brain node whose income radius the position lies within (spec: per brain node). */
    private static void grantEmeraldsToBrains(ServerLevel level, BlockPos pos) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        double r2 = (double) BrainNodeEvents.INCOME_RADIUS * BrainNodeEvents.INCOME_RADIUS;
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (!gp.dimension().equals(level.dimension())) continue;
            if (gp.pos().distSqr(pos) <= r2) data.econ(gp).addEmeralds(1);
        }
    }

    // --- emerald-unit death effects (spec 8) -------------------------------------------------------
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
        AABB box = new AABB(center).inflate(DEATH_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                u -> u.isAlive() && u != dead && Factions.isEmerald(u))) {
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
