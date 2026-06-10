package com.example.villagergunners;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.villagergunners.network.EmeraldTintPacket;
import com.example.villagergunners.network.ModNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

    /** Pending post-death explosions, processed when their corpse fades (~1s later). {@code deadMaxHp} drives
     *  the Emeraldger that rises after the blast (stage-4 only; {@code spawnEmeraldger} captured at death time). */
    private record Pending(ServerLevel level, double x, double y, double z, long when,
                           float deadMaxHp, boolean spawnEmeraldger) {}
    private static final List<Pending> PENDING = new ArrayList<>();

    // --- Alien Slasher on-hit payload ------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof LivingEntity attacker)) return;
        if (!(attacker.getMainHandItem().getItem() instanceof AlienSlasherItem)) return;

        // Virus respects immunity / one-shot rule; the rest of the payload always lands.
        EmeraldVirus.infect(target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS, 3, false, true)); // Slowness IV
        target.setSecondsOnFire(FIRE_SECONDS);
        applyHugeKnockback(attacker, target);
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
        Level level = e.level();
        if (level.isClientSide()) return;

        boolean active = EmeraldVirus.isActive(e);
        if (!active && EmeraldVirus.progress(e) <= 0.0F) return; // not, and never, infected

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
    }

    /** Infect not-yet-infected, non-immune creatures within {@link #SPREAD_RANGE} blocks (strong chance each). */
    private static void spread(LivingEntity host, Level level) {
        AABB box = host.getBoundingBox().inflate(SPREAD_RANGE);
        double rangeSqr = SPREAD_RANGE * SPREAD_RANGE;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box,
                x -> x != host && EmeraldVirus.canInfect(x) && x.distanceToSqr(host) <= rangeSqr);
        for (LivingEntity other : near) {
            if (host.getRandom().nextFloat() < SPREAD_CHANCE) {
                EmeraldVirus.infect(other);
            }
        }
    }

    /** A moving host rots the soft, solid block it walks on into a dirt path (50% per 5s). */
    private static void rotGroundUnder(LivingEntity e, Level level) {
        if (!e.onGround()) return;
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

        if (!(e.level() instanceof ServerLevel sl)) return;
        if (!EmeraldVirus.isActive(e)) return; // only hosts that die WHILE carrying the virus
        // At stage 4 the virus has evolved: after the blast, an Emeraldger rises from the corpse.
        boolean spawnEmeraldger = EmeraldVirus.isEvolved(sl.getServer()) && !(e instanceof EmeraldgerEntity);
        PENDING.add(new Pending(sl, e.getX(), e.getY() + 0.5, e.getZ(), sl.getGameTime() + CORPSE_DELAY,
                e.getMaxHealth(), spawnEmeraldger));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide() || !EmeraldVirus.isActive(e)) return;
        int n = 2 + e.getRandom().nextInt(3); // a few (2..4) emeralds
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY(), e.getZ(), new ItemStack(Items.EMERALD, n)));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level.getGameTime() < p.when) continue;
            explodeWithFire(p.level, p.x, p.y, p.z);
            if (p.spawnEmeraldger) spawnEmeraldger(p.level, p.x, p.y, p.z, p.deadMaxHp);
            it.remove();
        }
    }

    /** Raise an Emeraldger from a corpse: HP = 50% of the dead host, scaled body, armed as a stage-3 villager. */
    private static void spawnEmeraldger(ServerLevel level, double x, double y, double z, float deadMaxHp) {
        EmeraldgerEntity em = ModEntities.EMERALDGER.get().create(level);
        if (em == null) return;
        em.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        em.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null, null);
        em.setBaby(false);                 // always an adult-bodied warrior (its size comes from HP, not age)
        UpgradeState.setUpgraded(em);      // emeraldgers are born as stage-3 combatants (GunnerEvents arms them)
        em.initWithMaxHealthOfDead(deadMaxHp); // set HP/scale AFTER finalizeSpawn so it isn't overwritten
        level.addFreshEntity(em);
    }

    /** TNT-strength explosion that also leaves the whole blast range on fire. */
    private static void explodeWithFire(ServerLevel level, double x, double y, double z) {
        level.explode(null, x, y, z, BLAST_RADIUS, true, Level.ExplosionInteraction.TNT);
        BlockPos center = BlockPos.containing(x, y, z);
        int r = FIRE_PASS_RADIUS;
        for (BlockPos bp : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (center.distSqr(bp) > (long) r * r) continue;
            if (!level.getBlockState(bp).isAir()) continue;
            BlockPos below = bp.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                level.setBlockAndUpdate(bp.immutable(), Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
