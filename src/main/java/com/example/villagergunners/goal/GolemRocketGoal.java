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
import net.minecraft.world.phys.Vec3;

/**
 * Iron golem combat behaviour: stay at range and rocket the target, ONLY when no villager or other
 * iron golem is within {@link #ALLY_RADIUS} blocks of the target (so the splash never hits allies).
 *
 * The goal owns movement for the whole [0, SEE_RANGE] engagement (priority 0, MOVE flag) so the
 * golem's native melee goal can never reclaim it and walk the golem into point-blank range -- when
 * the target gets closer than {@link #FIRE_MIN} the golem backs away to regain a safe standoff
 * instead of rocketing itself. "Hostile" targeting is the golem's native one (any Enemy but creeper).
 */
public class GolemRocketGoal extends Goal {
    private static final double SEE_RANGE = 30.0;
    private static final double FIRE_MIN = 8.0;        // don't rocket closer than this (splash self-safety)
    private static final double REPOSITION_TO = 12.0;  // back away to this standoff (> FIRE_MIN = hysteresis)
    private static final double ALLY_RADIUS = 5.0;     // no ally within this of the target
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

    /**
     * Stay engaged for the whole [0,30] range + LOS so the goal keeps the MOVE flag from melee.
     * Restricted to hostiles ({@link Enemy}) so the golem never rockets a player (even if the golem's
     * native HurtByTargetGoal makes a player its target -- it'll just melee them, vanilla-style).
     */
    private boolean engageable(LivingEntity t) {
        return t != null && t.isAlive() && t instanceof Enemy
                && golem.distanceToSqr(t) <= SEE_RANGE * SEE_RANGE
                && golem.getSensing().hasLineOfSight(t);
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
        if (engageable(golem.getTarget())) return true;
        // No usable target yet: actively scan for the nearest hostile in range + LOS (throttled),
        // so the golem detects threats promptly instead of waiting on its native target goal.
        if (golem.tickCount % RETARGET_INTERVAL != 0) return false;
        LivingEntity found = GunUtil.findNearestHostile(golem, SEE_RANGE);
        if (found == null) return false;
        golem.setTarget(found);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return engageable(golem.getTarget());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = golem.getTarget();
        if (t == null) return;
        golem.getLookControl().setLookAt(t, 30.0F, 30.0F);

        if (cooldown > 0) cooldown--;

        boolean allyBlocks = !noAllyNearTarget(t);
        if (allyBlocks) {
            // An ally is too near the target: never rocket. Hold position (don't melee-rush either).
            golem.getNavigation().stop();
            return;
        }

        double d2 = golem.distanceToSqr(t);
        if (d2 < FIRE_MIN * FIRE_MIN) {
            // Too close to rocket safely -> back away from the target to regain standoff.
            Vec3 away = golem.position().subtract(t.position());
            if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
            Vec3 dest = t.position().add(away.normalize().scale(REPOSITION_TO));
            golem.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0D);
            return;
        }

        // In the firing band with a clear shot: hold ground, aim, and fire on cooldown.
        golem.getNavigation().stop();
        if (cooldown <= 0) {
            GunUtil.aimAt(golem, t);
            GunUtil.fire(golem, t, rocket, 0.0F); // 0 spread = perfect precision
            cooldown = ROCKET_COOLDOWN;
        }
    }
}
