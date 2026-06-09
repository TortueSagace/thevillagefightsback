package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.GunUtil;
import com.example.villagergunners.GunnerEvents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Generic gun combat for any PathfinderMob (villager, guard, ...): hold ground, face the hostile, and
 * shoot it with a "ranged" gun; switch to a "close" gun when the target is within {@link #FLAME_ON_SQR}.
 * A hostile is "seen" within {@link #SEE_RANGE} blocks AND with a direct line of sight. If a player
 * recently struck the mob (tracked in NBT, villagers only), that player becomes the target.
 */
public class GunCombatGoal extends Goal {
    private static final double SEE_RANGE = 30.0;
    // Weapon-switch dead band (squared): close weapon at <=5 blocks, back to ranged only past 6.5.
    private static final double FLAME_ON_SQR = 5.0 * 5.0;    // 25.0
    private static final double FLAME_OFF_SQR = 6.5 * 6.5;   // 42.25
    private static final int RETARGET_INTERVAL = 5; // ticks between target re-scans

    // Desperation: a defender that is badly hurt OR swarmed lobs a grenade and flees (natural panic AI)
    // for a few seconds, then snaps back to gun combat.
    private static final float DESPERATE_HEALTH_FRAC = 0.5F;  // health below half of max
    private static final int DESPERATE_ENEMY_COUNT = 5;       // OR this many hostiles...
    private static final double DESPERATE_ENEMY_RANGE = 10.0; // ...within this many blocks
    private static final int DESPERATE_SCAN_INTERVAL = 10;    // ticks between (throttled) swarm scans
    private static final int FLEE_TICKS = 60;                 // flee for 3 seconds
    private static final int GRENADE_FUSE = 40;              // grenade life before it detonates (~2s)
    private static final int GRENADE_COOLDOWN = 200;          // ~10s between panic grenades

    private final PathfinderMob shooter;
    private final boolean isVillager;
    private final ItemStack rangedGun;
    private final ItemStack closeGun;
    private final int rangedRate;
    private final int closeRate;

    private LivingEntity target;
    private int rangedCooldown;
    private int closeCooldown;
    private boolean usingClose; // weapon choice, with hysteresis (fired from cached stacks, not the hand)

    private long fleeUntil = Long.MIN_VALUE;          // game time until which we yield to natural flee AI
    private long grenadeCooldownUntil = Long.MIN_VALUE; // game time before which we won't throw again
    private long hostileScanTick = Long.MIN_VALUE;    // last swarm-scan time (throttle)
    private int nearbyHostiles;                       // cached swarm-scan result

    public GunCombatGoal(PathfinderMob shooter, ResourceLocation rangedId, float rangedBonus,
                         ResourceLocation closeId, float closeBonus) {
        this.shooter = shooter;
        this.isVillager = shooter instanceof Villager;
        this.rangedGun = GunUtil.makeGun(rangedId, rangedBonus);
        this.closeGun = GunUtil.makeGun(closeId, closeBonus);
        this.rangedRate = Math.max(3, GunUtil.fireRate(rangedGun, 4));
        this.closeRate = Math.max(2, GunUtil.fireRate(closeGun, 2));
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private LivingEntity acquireTarget() {
        Player aggro = GunnerEvents.getAggroPlayer(shooter);
        if (aggro != null && inSight(aggro)) return aggro;
        return GunUtil.findNearestHostile(shooter, SEE_RANGE);
    }

    private boolean inSight(LivingEntity t) {
        return t != null && t.isAlive()
                && shooter.distanceToSqr(t) <= SEE_RANGE * SEE_RANGE
                && shooter.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canUse() {
        if (rangedGun.isEmpty() && closeGun.isEmpty()) return false;
        if (isFleeing()) return false; // mid-panic: let the natural flee AI run, don't re-engage
        if (shooter.tickCount % RETARGET_INTERVAL != 0) return false; // throttle scans
        this.target = acquireTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (isFleeing()) return false; // desperation: yield to natural panic/flee behaviour
        return inSight(this.target);
    }

    /** True while a desperation grenade-and-flee is in progress (we stay disengaged from combat). */
    private boolean isFleeing() {
        return shooter.level().getGameTime() < fleeUntil;
    }

    /**
     * Desperate = badly hurt (&lt; half health) OR swarmed ({@value #DESPERATE_ENEMY_COUNT}+ hostiles
     * within {@value #DESPERATE_ENEMY_RANGE} blocks). The swarm scan is throttled to keep it cheap.
     */
    private boolean isDesperate(long now) {
        if (shooter.getHealth() < shooter.getMaxHealth() * DESPERATE_HEALTH_FRAC) return true;
        if (now - hostileScanTick >= DESPERATE_SCAN_INTERVAL) {
            hostileScanTick = now;
            nearbyHostiles = GunUtil.countHostilesWithin(shooter, DESPERATE_ENEMY_RANGE);
        }
        return nearbyHostiles >= DESPERATE_ENEMY_COUNT;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        shooter.setTarget(target);
    }

    @Override
    public void stop() {
        shooter.setTarget(null);
        this.target = null;
    }

    @Override
    public void tick() {
        LivingEntity t = this.target;
        if (t == null) return;

        // Desperation: badly hurt or swarmed -> lob JEG's grenade and flee for a few seconds. We return
        // immediately (no hold-ground / no panic-memory wipe this tick); canContinueToUse() then yields us
        // next tick so the mob's natural panic AI takes over until fleeUntil passes.
        long now = shooter.level().getGameTime();
        if (now >= grenadeCooldownUntil && isDesperate(now)) {
            GunUtil.aimAt(shooter, t);                 // face the threat so the lob goes toward it
            GunUtil.throwGrenade(shooter, GRENADE_FUSE);
            fleeUntil = now + FLEE_TICKS;
            grenadeCooldownUntil = now + GRENADE_COOLDOWN;
            return;
        }

        // Hold ground and face the target. Villagers are brain-driven and would flee, so (only for
        // villagers) erase the panic-trigger memories before the brain ticks so panic never fires.
        if (isVillager) {
            Brain<?> brain = shooter.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.HURT_BY);
            brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
        }
        shooter.getNavigation().stop();
        shooter.getLookControl().setLookAt(t, 30.0F, 30.0F);
        shooter.setTarget(t);

        if (rangedCooldown > 0) rangedCooldown--;
        if (closeCooldown > 0) closeCooldown--;

        double dSqr = shooter.distanceToSqr(t);
        usingClose = usingClose ? (dSqr <= FLAME_OFF_SQR) : (dSqr <= FLAME_ON_SQR);
        if (usingClose && !closeGun.isEmpty()) {
            if (closeCooldown <= 0) {
                GunUtil.aimAt(shooter, t);
                GunUtil.fire(shooter, t, closeGun, 0.0F); // 0 spread = perfect precision
                closeCooldown = closeRate;
            }
        } else if (!rangedGun.isEmpty()) {
            if (rangedCooldown <= 0) {
                GunUtil.aimAt(shooter, t);
                GunUtil.fire(shooter, t, rangedGun, 0.0F);
                rangedCooldown = rangedRate;
            }
        }
    }
}
