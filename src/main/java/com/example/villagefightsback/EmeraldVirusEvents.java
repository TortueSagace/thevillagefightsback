package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.villagefightsback.network.EmeraldTintPacket;
import com.example.villagefightsback.network.ModNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * All Emerald-Virus behaviour: the Alien Slasher's on-hit payload, the virus's contagion / ground-rot /
 * green-tint sync while active, and the post-death TNT explosion + emerald loot. Immunity and the
 * one-shot infection rule live in {@link EmeraldVirus}.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldVirusEvents {
    private EmeraldVirusEvents() {}

    // --- tuning ----------------------------------------------------------------------------------
    private static final int SLOWNESS_TICKS = 60;       // Slowness IV for 3 seconds on hit
    private static final int FIRE_SECONDS = 5;          // sets the target ablaze on hit
    private static final double KNOCKBACK_POWER = 2.6;  // horizontal launch speed (~20 blocks after air drag)
    private static final double KNOCKBACK_LIFT = 1.0;   // upward kick so the victim sails

    private static final double SPREAD_RANGE = 3.0;     // virus jumps to creatures within this many blocks
    private static final float SPREAD_CHANCE = 0.5F;    // strong per-check transmission probability
    private static final int SPREAD_INTERVAL = 40;      // check every 2s
    private static final int PATH_INTERVAL = 100;       // ground-rot check every 5s
    private static final float PATH_CHANCE = 0.5F;      // 50% to convert the block under a moving host
    private static final float MAX_PATH_RESISTANCE = 1200.0F; // only blocks softer than obsidian rot
    private static final int TINT_SYNC_INTERVAL = 20;   // broadcast tint progress ~1/s

    private static final int CORPSE_DELAY = 20;         // ~1s after death (when the corpse fades) -> explode
    private static final float BLAST_RADIUS = 4.0F;     // TNT-strength blast
    private static final int FIRE_PASS_RADIUS = 4;      // set the whole blast range on fire

    /** What rises from the corpse after the (optional) blast. */
    private static final int SPAWN_NONE = 0, SPAWN_MITE = 1, SPAWN_EMERALDGER = 2;

    /** Pending post-death explosion, fired when the corpse fades (~1s later). We keep the corpse ENTITY (not a
     *  fixed point), because the Alien Slasher's huge knockback keeps flinging the dead body during its death
     *  animation -- so we explode where the corpse actually ends up, not where it died. {@code deadMaxHp} drives
     *  the Emeraldger that rises after the blast; {@code spawnKind} is SPAWN_MITE (Virus I) or SPAWN_EMERALDGER
     *  (Virus II, stage-4 only). */
    private record Pending(ServerLevel level, LivingEntity corpse, long when, float deadMaxHp, int spawnKind) {}
    private static final List<Pending> PENDING = new ArrayList<>();

    // --- Alien Slasher on-hit payload ------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level.isClientSide()) return;
        Entity direct = event.getSource().getDirectEntity();
        // Emerald-virus tipped arrow (a stage-3 guard's crossbow): infect on hit, nothing else.
        if (direct instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && arrow.getPersistentData().getBoolean(com.example.villagefightsback.goal.StageThreeGuardGoal.NBT_VIRUS_ARROW)) {
            EmeraldVirus.infect(target, virusLevel(arrow.getOwner())); // stage-4 guard's arrow -> Virus II
            return;
        }
        if (!(direct instanceof LivingEntity attacker)) return;
        // Item-driven virus level: the Alien Eradicator inflicts Virus II, the Alien Slasher always Virus I.
        net.minecraft.world.item.Item weapon = attacker.getMainHandItem().getItem();
        int meleeAmplifier;
        if (weapon instanceof AlienEradicatorItem) meleeAmplifier = 1;       // Eradicator -> Virus II
        else if (weapon instanceof AlienSlasherItem) meleeAmplifier = 0;     // Slasher -> Virus I (always)
        else return;                                                         // not one of our melee weapons

        // Virus respects immunity / one-shot rule; the rest of the payload always lands.
        EmeraldVirus.infect(target, meleeAmplifier);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS, 3, false, true)); // Slowness IV
        target.setSecondsOnFire(FIRE_SECONDS);
        applyHugeKnockback(attacker, target);
    }

    /** Emerald Virus amplifier a guard's CROSSBOW virus arrow imparts: 1 (Virus II) ONLY for a stage-4 guard.
     *  Vindictive creatures (the unleashed boss's minions) never impart Virus II -- always Virus I. The melee
     *  weapons no longer use this -- their level is fixed by the item (Slasher = I, Eradicator = II). */
    private static int virusLevel(Entity attacker) {
        if (attacker == null) return 0;
        if (attacker instanceof VindictiveGuardEntity || attacker instanceof VindictiveGigachadEntity) return 0;
        return UpgradeState.isStage4(attacker) ? 1 : 0;
    }

    /**
     * Emerald CREATURES' on-hit payload (this replaces the old Emerald Infection): every hit from an emerald-faction
     * unit imparts the Emerald Virus. At a STAGE-4 village it imparts Virus II; below stage 4, Virus I. We key off
     * the damage source's responsible entity, so melee hits, skeleton arrows, creeper blasts and wither skulls all
     * count, and we use LivingAttackEvent so it lands even on a hit that deals no final damage. Spared factions
     * (fellow emeralds, the village, the vindictive) are never infected.
     */
    @SubscribeEvent
    public static void onEmeraldAttack(LivingAttackEvent event) {
        if (event.getEntity().level.isClientSide() || !Config.mobInfectionEnabled()) return;
        if (!Factions.isEmerald(event.getSource().getEntity())) return;
        LivingEntity victim = event.getEntity();
        if (Factions.isEmerald(victim) || Factions.isVillage(victim) || Factions.isInfected(victim)) return;
        if (!(victim.level instanceof ServerLevel sl)) return;
        EmeraldVirus.infect(victim, stage4Village(sl, victim.blockPosition()) ? 1 : 0); // stage 4 -> Virus II
    }

    /** True if any Village Brain Node within income range of {@code pos} has reached stage 4 -- so emerald units
     *  there impart Virus II rather than Virus I (keeping Virus II a stage-4-only thing). */
    private static boolean stage4Village(ServerLevel level, BlockPos pos) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        double r2 = (double) BrainNodeEvents.INCOME_RADIUS * BrainNodeEvents.INCOME_RADIUS;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(level.dimension()) && gp.pos().distSqr(pos) <= r2
                    && data.econ(gp).getLevel() >= 4) return true;
        }
        return false;
    }

    /** Launch the target ~20 blocks away from the attacker, bypassing knockback resistance (direct velocity set). */
    private static void applyHugeKnockback(LivingEntity attacker, LivingEntity target) {
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) { // attacker standing on the target: shove along the attacker's facing
            float yaw = attacker.getYRot() * ((float) Math.PI / 180F);
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
            len = 1.0;
        }
        dx /= len;
        dz /= len;
        target.setDeltaMovement(dx * KNOCKBACK_POWER, KNOCKBACK_LIFT, dz * KNOCKBACK_POWER);
        target.hasImpulse = true;
        target.hurtMarked = true; // re-sync velocity to the client so the launch is visible
    }

    // --- per-host upkeep: tint growth + sync, contagion, ground-rot -------------------------------
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        Level level = e.level;
        if (level.isClientSide()) return;

        // Reject the no-op path (uninfected entities -- nearly all of them) BEFORE touching the profiler:
        // this event fires for every living entity every tick, so instrumenting the bail path just measures
        // the profiler's own nanoTime+computeIfAbsent overhead. Gate first, then profile only real work.
        boolean active = EmeraldVirus.isActive(e);
        if (!active && EmeraldVirus.progress(e) <= 0.0F) return; // not, and never, infected

        long _p = ModProfiler.now();
        try {
            if (active) {
                EmeraldVirus.setProgress(e, EmeraldVirus.progress(e) + 1.0F / EmeraldVirus.DURATION_TICKS);
            }
            if (e.tickCount % TINT_SYNC_INTERVAL == 0) {
                ModNetwork.sendToTracking(e, new EmeraldTintPacket(e.getId(), EmeraldVirus.progress(e)));
            }
            if (!active) return; // cured but still green: no more spreading / ground-rot

            if (e.tickCount % SPREAD_INTERVAL == 0) {
                spread(e, level);
            }
            if (e.tickCount % PATH_INTERVAL == 0) {
                rotGroundUnder(e, level);
            }
        } finally {
            ModProfiler.add("virus.entityTick", _p);
        }
    }

    /** Infect not-yet-infected, non-immune creatures within {@link #SPREAD_RANGE} blocks (strong chance each). The
     *  contagion carries the HOST's own virus level, so a Virus II host (a stage-4 infection) spreads Virus II. A
     *  fresh contagion infection awards nearby players "What about social distancing?". */
    private static void spread(LivingEntity host, Level level) {
        net.minecraft.world.effect.MobEffectInstance hv = host.getEffect(ModEffects.EMERALD_VIRUS.get());
        int amp = hv != null ? hv.getAmplifier() : 0;
        AABB box = host.getBoundingBox().inflate(SPREAD_RANGE);
        double rangeSqr = SPREAD_RANGE * SPREAD_RANGE;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box,
                x -> x != host && EmeraldVirus.canInfect(x) && x.distanceToSqr(host) <= rangeSqr);
        for (LivingEntity other : near) {
            if (host.getRandom().nextFloat() < SPREAD_CHANCE && EmeraldVirus.infect(other, amp)
                    && level instanceof ServerLevel sl) {
                Achievements.awardNearby(sl, other.getX(), other.getY(), other.getZ(), 48.0,
                        "brain_node/what_about_social_distancing"); // infected by the contagion, not a direct hit
            }
        }
    }

    /** A moving host rots the soft, solid block it walks on into a dirt path (50% per 5s). */
    private static void rotGroundUnder(LivingEntity e, Level level) {
        if (!e.isOnGround()) return;
        double moved = Math.abs(e.getX() - e.xOld) + Math.abs(e.getZ() - e.zOld);
        if (moved < 1.0E-3) return; // standing still

        BlockPos pos = e.getOnPos(); // the block just below the host's feet
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.DIRT_PATH)) return;
        if (state.getBlock().getExplosionResistance() >= MAX_PATH_RESISTANCE) return; // obsidian-tough or harder
        if (!state.isFaceSturdy(level, pos, Direction.UP)) return; // needs a flat solid top to host a path
        if (e.getRandom().nextFloat() < PATH_CHANCE) {
            level.setBlockAndUpdate(pos, Blocks.DIRT_PATH.defaultBlockState());
        }
    }

    // --- send the tint to players who newly start tracking an already-infected host --------------
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity le && EmeraldVirus.progress(le) > 0.0F
                && event.getEntity() instanceof ServerPlayer sp) {
            ModNetwork.sendTo(sp, new EmeraldTintPacket(le.getId(), EmeraldVirus.progress(le)));
        }
    }

    // --- death: schedule the TNT blast and add emerald loot --------------------------------------
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        // An emeraldger that just got a kill renews its 5-minute no-kill lifespan.
        if (event.getSource().getEntity() instanceof EmeraldgerEntity killer) killer.markKill();

        if (!(e.level instanceof ServerLevel sl)) return;
        if (Factions.isEmerald(e)) return;     // emerald units: their death is handled by EmeraldConversion.onEmeraldDeath
        if (!EmeraldVirus.isActive(e)) return; // only hosts that die WHILE carrying the virus

        // The merged "infection" powers (formerly Emerald Infection) are FACTION machinery -- they only run when mob
        // infection is enabled, matching the master gate (EmeraldPopulation.onEmeraldJoinGate). With it OFF the virus
        // is just a Wither debuff: no organic mass, no green swirl, no infested path, and nothing rises from the
        // corpse. The explosion is independent (its own enable_emerald_virus_explosion config), so the corpse entry
        // is still scheduled for it.
        int spawnKind = SPAWN_NONE;
        if (Config.mobInfectionEnabled()) {
            LimboBanishFx.startLime(e);                              // green death swirl
            EmeraldConversion.placeInfestedPathUnder(sl, e);        // infested path under the corpse
            // Organic mass is banked when the CORPSE UNIT actually erupts (see onServerTick), and ONLY if the area
            // is below the cap. At the cap the corpse unit's HP is poured into the nearest emerald instead, so the
            // brain must not ALSO bank organic mass -- one death yields one reward, not two.
            // A host that died carrying Virus II (amplifier >= 1) -> an Emeraldger; a Virus-I death -> an emerald mite.
            // Virus II is dealt only at stage 4 (Alien Eradicator, stage-4 guard crossbow, stage-4 emerald units), so
            // emeraldgers only ever appear at stage 4.
            net.minecraft.world.effect.MobEffectInstance vinst = e.getEffect(ModEffects.EMERALD_VIRUS.get());
            boolean virusII = vinst != null && vinst.getAmplifier() >= 1;
            spawnKind = virusII ? SPAWN_EMERALDGER : SPAWN_MITE;
        }
        PENDING.add(new Pending(sl, e, sl.getGameTime() + CORPSE_DELAY, e.getMaxHealth(), spawnKind));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level.isClientSide() || !EmeraldVirus.isActive(e)) return;
        // A host that dies carrying the virus has its whole body banked as organic mass by the nearest brain node
        // (see onDeath), so the corpse leaves NOTHING -- no emeralds, no vanilla loot. Only true emerald-faction
        // units drop an emerald, and they do so via their own loot tables (they are immune to the virus, so they
        // never reach this branch). When mob infection is OFF the virus is just a debuff and the brain banks
        // nothing, so the corpse is left to drop normally.
        if (Config.mobInfectionEnabled()) event.getDrops().clear();
    }

    /** Per-HP chance an emerald-faction unit drops ONE emerald on death: 2.5% per max-HP point, so a 20-HP unit
     *  drops it ~50% of the time and a 4-HP unit ~10%, capped at 100% (heavy units always drop one). */
    private static final float EMERALD_DROP_PER_HP = 0.025F;

    /**
     * Emerald gem drop: an emerald-faction unit drops ONE emerald with a chance proportional to its max HP
     * ({@link #EMERALD_DROP_PER_HP} per point, capped at 100%). Replaces the old guaranteed loot-table pool, which
     * a static table couldn't make HP-dependent. Emerald units are immune to the virus, so {@link #onDrops} never
     * touches them; their normal (vanilla-variant) loot still comes from their loot table.
     */
    @SubscribeEvent
    public static void onEmeraldGemDrop(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level.isClientSide() || !Factions.isEmerald(e)) return;
        if (e instanceof EmeraldEndermite) return; // emerald mites are transient swarm units -> never drop an emerald
        float chance = Math.min(1.0F, e.getMaxHealth() * EMERALD_DROP_PER_HP);
        if (e.getRandom().nextFloat() >= chance) return;
        ItemEntity gem = new ItemEntity(e.level, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(Items.EMERALD));
        gem.setDefaultPickUpDelay();
        event.getDrops().add(gem);
    }

    /** A virus host's experience is consumed along with its organic mass -> no XP orbs either (only when the
     *  feature is on; otherwise the virus is a plain debuff and XP drops normally). */
    @SubscribeEvent
    public static void onXpDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent event) {
        if (Config.mobInfectionEnabled() && EmeraldVirus.isActive(event.getEntity())) event.setCanceled(true);
    }

    private static final int MAX_DETONATIONS_PER_TICK = 8; // throttle so a backlog can't freeze a tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        long _p = ModProfiler.now();
        // Drain a few due entries and remove them FIRST, then explode. We remove before exploding for two
        // reasons: (1) the blast kills nearby virus hosts whose onDeath re-adds to PENDING, so we must not be
        // mid-iteration (ConcurrentModificationException); (2) on world load every queued corpse comes due at
        // once -- firing them all in one tick is an 80s+ freeze -- so we cap per tick and let the rest wait.
        java.util.List<Pending> due = new java.util.ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext() && due.size() < MAX_DETONATIONS_PER_TICK) {
            Pending p = it.next();
            if (p.level.getGameTime() < p.when) continue;          // timer not up yet -> leave it queued
            BlockPos at = new BlockPos(p.corpse.getX(), p.corpse.getY() + 0.5, p.corpse.getZ());
            // Never explode into an unloaded chunk: Explosion#explode reads block states across the blast,
            // which force-loads chunks via a blocking join() and can deadlock the server tick. Nobody is near
            // an unloaded chunk to see it anyway, so just drop the entry.
            if (!p.level.hasChunkAt(at)) { it.remove(); continue; }
            due.add(p);
            it.remove();
        }
        for (Pending p : due) {
            // Explode where the CORPSE ended up (it kept sliding from the knockback during its death
            // animation), not where it died. After removal the entity still reports its last position.
            double x = p.corpse.getX(), y = p.corpse.getY() + 0.5, z = p.corpse.getZ();
            if (Config.emeraldVirusExplosion()) explodeWithFire(p.level, x, y, z); // master switch (no boom if off)
            // Bank the host's organic mass for the nearest brain -- but ONLY if its corpse unit will really spawn.
            // At the population cap the corpse unit can't spawn (its HP is poured into the nearest emerald by
            // EmeraldPopulation.absorbInto), so banking here too would be a second reward from a single death.
            if (p.spawnKind != SPAWN_NONE
                    && !EmeraldPopulation.atCap(p.level, new net.minecraft.world.phys.Vec3(x, y, z))) {
                OrganicMass.addToNearestBrain(p.level, new BlockPos(x, y, z), Math.round(p.deadMaxHp));
            }
            if (p.spawnKind == SPAWN_EMERALDGER) {
                spawnEmeraldger(p.level, x, y, z, p.deadMaxHp);
            } else if (p.spawnKind == SPAWN_MITE) {
                // Virus I -> an emerald mite (capped: EmeraldPopulation absorbs/cancels it when already at the cap).
                EmeraldConversion.spawnAt(p.level, new BlockPos(x, y, z), ModEntities.EMERALD_ENDERMITE.get(), null);
            }
        }
        ModProfiler.add("virus.explode", _p);
    }

    /** Raise an Emeraldger from a corpse: HP = 50% of the dead host, scaled body, armed as a stage-3 villager. */
    private static void spawnEmeraldger(ServerLevel level, double x, double y, double z, float deadMaxHp) {
        EmeraldgerEntity em = ModEntities.EMERALDGER.get().create(level);
        if (em == null) return;
        em.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        em.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(x, y, z)),
                net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null, null);
        em.setBaby(false);                 // always an adult-bodied warrior (its size comes from HP, not age)
        UpgradeState.setUpgraded(em);      // emeraldgers are born as stage-3 combatants (VillageEvents arms them)
        em.initWithMaxHealthOfDead(deadMaxHp); // set HP/scale AFTER finalizeSpawn so it isn't overwritten
        level.addFreshEntity(em);
    }

    /** TNT-strength blast. The blast ALWAYS hurts units; whether it breaks blocks and whether it leaves the
     *  blast range on fire are independently config-gated (emerald_virus_explosion_destroy_blocks / _set_blocks_on_fire). */
    private static void explodeWithFire(ServerLevel level, double x, double y, double z) {
        // NONE still damages entities but destroys no blocks; TNT also destroys blocks.
        net.minecraft.world.level.Explosion.BlockInteraction interaction = Config.emeraldVirusDestroysBlocks()
                ? net.minecraft.world.level.Explosion.BlockInteraction.DESTROY : net.minecraft.world.level.Explosion.BlockInteraction.NONE;
        level.explode(null, x, y, z, BLAST_RADIUS, false, interaction); // engine fire off; we scorch separately
        if (Config.emeraldVirusSetsFire()) ModExplosions.scorch(level, new BlockPos(x, y, z), FIRE_PASS_RADIUS);
    }
}
