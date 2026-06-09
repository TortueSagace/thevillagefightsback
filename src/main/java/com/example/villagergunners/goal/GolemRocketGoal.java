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
    private static final double MIN_RANGE = 8.0;       // below this, melee instead of rocket (splash safety)
    private static final double ALLY_RADIUS = 5.0;     // no villager/golem within this of the target
    private static final int ROCKET_COOLDOWN = 60;     // ticks between rockets (~3s)
    private static final int ALLY_SCAN_INTERVAL = 5;   // ticks between (expensive) ally scans
    private static final int RETARGET_INTERVAL = 5;    // ticks between hostile re-scans

    private final IronGolem golem;
    private final ItemStack rocket;
    private int cooldown;
    private int allyScanTick = -1;
    private boolean allyClear;

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
        if (d2 < MIN_RANGE * MIN_RANGE) return false;        // too close -> melee handles it
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
        LivingEntity cur = golem.getTarget();
        if (!isValidHostile(cur)) {
            // Acquire the nearest visible hostile (throttled) and set it as the target so the golem's
            // melee/approach goals can act on it even when we can't rocket it.
            if (golem.tickCount % RETARGET_INTERVAL == 0) {
                LivingEntity found = GunUtil.findNearestHostile(golem, SEE_RANGE);
                if (found != null) golem.setTarget(found);
            }
            cur = golem.getTarget();
        }
        return canFire(cur);   // only ACTIVATE when we can cleanly rocket; else native melee takes over
    }

    @Override
    public boolean canContinueToUse() {
        return canFire(golem.getTarget());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        golem.getNavigation().stop(); // keep the target so native melee can finish the job
    }

    @Override
    public void tick() {
        LivingEntity t = golem.getTarget();
        if (t == null) return;
        golem.getNavigation().stop(); // hold ground at range and fire
        golem.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        GunUtil.aimAt(golem, t);
        GunUtil.fire(golem, t, rocket, 0.0F);
        cooldown = ROCKET_COOLDOWN;
    }
}
