package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.GunUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Iron golem ranged behaviour: rocket a hostile when it can do so SAFELY, otherwise step aside and let
 * the golem's native melee AI handle it. "Safely" = the target is a hostile ({@link Enemy}) within
 * [MIN_RANGE, SEE_RANGE], in line of sight, with NO villager/golem within {@link #ALLY_RADIUS} of it.
 *
 * The goal only activates when it can actually fire, so a point-blank target (too close to rocket) is
 * left to the golem's melee goal (the golem punches it) instead of the goal awkwardly holding/kiting.
 * We still set the target when one is found so the native melee/approach goals have something to act on.
 */
public class GolemRocketGoal extends Goal {
    private static final double SEE_RANGE = 30.0;
    private static final double MIN_RANGE = 8.0;       // below this, do the wind-charge pogo (splash safety)
    private static final double ALLY_RADIUS = 5.0;     // no villager/golem within this of the target
    private static final int ROCKET_COOLDOWN = 60;     // ticks between rockets (~3s)
    private static final int ALLY_SCAN_INTERVAL = 5;   // ticks between (expensive) ally scans
    private static final int RETARGET_INTERVAL = 5;    // ticks between hostile re-scans

    // Wind-charge "pogo" combo for point-blank targets: launch up, fire the rocket at the apex.
    private static final double LAUNCH_VELOCITY = 1.05; // upward impulse (~6 blocks of height)
    private static final int COMBO_COOLDOWN = 100;     // ticks between pogo combos (~5s)
    private static final int MAX_RISE_TICKS = 60;      // safety: abort the combo if we never apex

    private final IronGolem golem;
    private final ItemStack rocket;
    private int cooldown;
    private int comboCooldown;
    private int allyScanTick = -1;
    private boolean allyClear;

    // Combo state.
    private boolean rising;
    private boolean launchedAndRose;
    private int riseTimer;

    public GolemRocketGoal(IronGolem golem) {
        this.golem = golem;
        this.rocket = GunUtil.makeGun(GunUtil.ROCKET_LAUNCHER);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** A hostile we care about: alive Enemy within detection range. */
    private boolean isValidHostile(LivingEntity t) {
        return t != null && t.isAlive() && t instanceof Enemy
                && golem.distanceToSqr(t) <= SEE_RANGE * SEE_RANGE;
    }

    /** Can we rocket this target right now (in range band, LOS, no ally near it)? */
    private boolean canFire(LivingEntity t) {
        if (!isValidHostile(t)) return false;
        double d2 = golem.distanceToSqr(t);
        if (d2 < MIN_RANGE * MIN_RANGE) return false;        // too close -> pogo combo handles it
        if (!golem.getSensing().hasLineOfSight(t)) return false;
        return noAllyNearTarget(t);
    }

    /** Target too close to rocket safely -> launch with a wind charge and fire at the apex. */
    private boolean canCombo(LivingEntity t) {
        if (!isValidHostile(t)) return false;
        if (comboCooldown > 0 || !GunUtil.windChargeAvailable()) return false;
        if (golem.distanceToSqr(t) >= MIN_RANGE * MIN_RANGE) return false; // only when too close
        if (!golem.getSensing().hasLineOfSight(t)) return false;
        return noAllyNearTarget(t);
    }

    /** The golem "knows" ally positions around the target even without seeing them. Throttled. */
    private boolean noAllyNearTarget(LivingEntity t) {
        int now = golem.tickCount;
        if (allyScanTick < 0 || now - allyScanTick >= ALLY_SCAN_INTERVAL) {
            allyScanTick = now;
            AABB box = t.getBoundingBox().inflate(ALLY_RADIUS);
            allyClear = golem.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != golem && e.isAlive() && (e instanceof Villager || e instanceof IronGolem)).isEmpty();
        }
        return allyClear;
    }

    @Override
    public boolean canUse() {
        if (rocket.isEmpty()) return false;
        if (rising) return true;                 // mid-combo: keep ticking to reach the apex
        LivingEntity cur = golem.getTarget();
        if (!isValidHostile(cur)) {
            // Acquire the nearest visible hostile (throttled) and set it as the target so the golem's
            // melee/approach goals can act on it even when we can't engage it ourselves.
            if (golem.tickCount % RETARGET_INTERVAL == 0) {
                LivingEntity found = GunUtil.findNearestHostile(golem, SEE_RANGE);
                if (found != null) golem.setTarget(found);
            }
            cur = golem.getTarget();
        }
        return canFire(cur) || canCombo(cur); // activate to rocket at range OR pogo a point-blank target
    }

    @Override
    public boolean canContinueToUse() {
        if (rising) return true;
        LivingEntity t = golem.getTarget();
        return canFire(t) || canCombo(t);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        golem.getNavigation().stop(); // keep the target so native melee can finish the job
        rising = false;
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--;
        if (comboCooldown > 0) comboCooldown--;

        if (rising) {
            tickRising();
            return;
        }

        LivingEntity t = golem.getTarget();
        if (t == null) return;
        golem.getLookControl().setLookAt(t, 30.0F, 30.0F);

        double d2 = golem.distanceToSqr(t);
        if (d2 < MIN_RANGE * MIN_RANGE) {
            // Too close: start the wind-charge pogo (canUse/canCombo already verified it's allowed).
            startCombo();
            return;
        }

        // In the firing band: hold ground, aim, fire on cooldown.
        golem.getNavigation().stop();
        if (cooldown <= 0) {
            GunUtil.aimAt(golem, t);
            GunUtil.fire(golem, t, rocket, 0.0F);
            cooldown = ROCKET_COOLDOWN;
        }
    }

    private void startCombo() {
        GunUtil.throwWindChargeDown(golem); // visual/thematic launch (soft dep)
        // Iron golems resist knockback, so apply the upward impulse directly to guarantee the launch.
        golem.setDeltaMovement(golem.getDeltaMovement().x * 0.2, LAUNCH_VELOCITY, golem.getDeltaMovement().z * 0.2);
        golem.hasImpulse = true;
        golem.hurtMarked = true; // sync the new velocity to clients so the golem visibly flies up
        rising = true;
        launchedAndRose = false;
        riseTimer = 0;
        comboCooldown = COMBO_COOLDOWN;
    }

    private void tickRising() {
        riseTimer++;
        LivingEntity t = golem.getTarget();
        if (t != null) golem.getLookControl().setLookAt(t, 30.0F, 30.0F);

        double vy = golem.getDeltaMovement().y;
        if (!launchedAndRose && vy > 0.2) launchedAndRose = true;

        // Apex = we were rising and have started to fall (non-positive vertical velocity).
        boolean apex = launchedAndRose && vy <= 0.0;
        if (apex || riseTimer > MAX_RISE_TICKS) {
            if (apex && t != null && isValidHostile(t)
                    && golem.getSensing().hasLineOfSight(t) && noAllyNearTarget(t)) {
                GunUtil.aimAt(golem, t);
                GunUtil.fire(golem, t, rocket, 0.0F); // fire down at the target from the apex
                cooldown = ROCKET_COOLDOWN;
            }
            rising = false;
        }
    }
}
