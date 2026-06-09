package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.FlareRally;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * When a reinforcement flare is fired, idle defenders (no current target) run toward the flare's
 * origin -- waking from sleep if needed. Each defender reacts to a given flare only once; it stops as
 * soon as it has a reason to fight (its higher-priority combat goal acquires a target and preempts
 * this one), and won't run to that same flare again. A new flare re-arms the response.
 */
public class FlareRallyGoal extends Goal {
    private static final double RESPONSE_RANGE = 80.0; // only defenders within this of the flare answer
    private static final double ARRIVE_DIST = 4.0;     // close enough = arrived
    private static final int REPATH_INTERVAL = 10;     // ticks between re-paths
    private static final double RUN_SPEED = 1.15;      // move briskly to the rally

    private final PathfinderMob mob;
    private final boolean isVillager;
    private long respondedFlare = Long.MIN_VALUE;
    private int repathTimer;

    public FlareRallyGoal(PathfinderMob mob) {
        this.mob = mob;
        this.isVillager = mob instanceof Villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean fighting() {
        return mob.getTarget() != null && mob.getTarget().isAlive();
    }

    private double distSqrToRally() {
        double dx = FlareRally.x() - mob.getX();
        double dy = FlareRally.y() - mob.getY();
        double dz = FlareRally.z() - mob.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean canUse() {
        if (fighting()) return false; // only idle defenders rally
        long now = mob.level().getGameTime();
        if (!FlareRally.isActive(mob.level(), now)) return false;
        if (FlareRally.flareTime() <= respondedFlare) return false; // already answered this flare
        if (distSqrToRally() > RESPONSE_RANGE * RESPONSE_RANGE) return false; // too far to bother
        respondedFlare = FlareRally.flareTime(); // answer this flare exactly once
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (fighting()) return false; // saw a reason to fight -> stop and forget this flare
        if (!FlareRally.isActive(mob.level(), mob.level().getGameTime())) return false;
        return distSqrToRally() > ARRIVE_DIST * ARRIVE_DIST; // until we arrive
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        repathTimer = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (isVillager) {
            if (mob.isSleeping()) mob.stopSleeping();              // wake up and answer the call
            mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); // don't let the brain override us
        }
        mob.getLookControl().setLookAt(FlareRally.x(), FlareRally.y(), FlareRally.z());
        if (repathTimer-- <= 0) {
            mob.getNavigation().moveTo(FlareRally.x(), FlareRally.y(), FlareRally.z(), RUN_SPEED);
            repathTimer = REPATH_INTERVAL;
        }
    }
}
