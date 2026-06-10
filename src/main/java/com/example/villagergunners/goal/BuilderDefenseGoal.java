package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.BuilderRole;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Builder self-defense. A builder normally keeps building and does NOT go hunting -- the wall is the
 * priority. But a hostile within {@value #ENGAGE_RADIUS} blocks is clearly after the builder and can't be
 * ignored: the builder stops building, stands its ground, and melees it (flat {@value #DAMAGE} dmg) until
 * it's dead or has moved past {@value #LEAVE_RADIUS} blocks, then it resumes building. Higher priority than
 * the build goal so it interrupts it. Creepers are excepted (meleeing one is suicide near the wall).
 */
public class BuilderDefenseGoal extends Goal {
    private static final double ENGAGE_RADIUS = 3.0;  // a threat this close must be dealt with
    private static final double LEAVE_RADIUS = 4.0;   // keep fighting until it's dead or beyond this
    private static final double REACH_SQR = 4.0;      // melee reach (~2 blocks)
    private static final float DAMAGE = 8.0F;         // drops a weak mob in a couple of swings
    private static final int ATTACK_COOLDOWN = 16;    // ~0.8s between swings
    private static final int SCAN_INTERVAL = 4;       // throttle the (builder-only) proximity scan

    private final Villager villager;
    private LivingEntity target;
    private int attackCooldown;

    public BuilderDefenseGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!BuilderRole.isBuilder(villager)) return false;             // only builders defend their build
        if (villager.tickCount % SCAN_INTERVAL != 0) return false;      // cheap: scan a few times a second
        this.target = nearestEnemy(ENGAGE_RADIUS);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return BuilderRole.isBuilder(villager) && target != null && target.isAlive()
                && villager.distanceToSqr(target) <= LEAVE_RADIUS * LEAVE_RADIUS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        villager.setTarget(target);
    }

    @Override
    public void stop() {
        villager.setTarget(null);
        this.target = null;
        villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = this.target;
        if (t == null) return;

        // Stand and fight: erase the brain's flee/wander so the build-villager doesn't run from a mob it
        // can clearly kill (the thing that was getting builders killed).
        Brain<?> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);

        villager.setTarget(t);
        villager.getLookControl().setLookAt(t, 30.0F, 30.0F);
        villager.getNavigation().moveTo(t, 1.2D);

        if (attackCooldown > 0) attackCooldown--;
        if (villager.distanceToSqr(t) <= REACH_SQR && attackCooldown <= 0) {
            villager.swing(InteractionHand.MAIN_HAND);
            t.hurt(villager.level().damageSources().mobAttack(villager), DAMAGE);
            attackCooldown = ATTACK_COOLDOWN;
        }
    }

    /** Nearest living hostile (not a creeper) within {@code radius} blocks. */
    private LivingEntity nearestEnemy(double radius) {
        AABB box = villager.getBoundingBox().inflate(radius);
        LivingEntity nearest = null;
        double best = radius * radius;
        for (LivingEntity e : villager.level().getEntitiesOfClass(LivingEntity.class, box,
                x -> x instanceof Enemy && !(x instanceof Creeper) && x.isAlive() && x != villager)) {
            double d = villager.distanceToSqr(e);
            if (d <= best) { best = d; nearest = e; }
        }
        return nearest;
    }
}
