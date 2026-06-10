package com.example.villagergunners.goal;

import java.util.EnumSet;
import java.util.List;

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
 * Nerfed (un-upgraded) villager combat: a villager flees normally (vanilla brain) until it drops below
 * {@value #HEALTH_FRAC} of its health, at which point it makes a desperate stand -- meleeing the nearest
 * hostile (NEVER a creeper) with its stone axe. It does not fight while healthy, so the panic/flee
 * behaviour is left intact above this threshold.
 */
public class StoneAxeDefenseGoal extends Goal {
    private static final float HEALTH_FRAC = 0.75F;  // only fight below 75% health
    private static final double SEE_RANGE = 16.0;    // detect hostiles within this
    private static final double REACH_SQR = 4.0;     // melee reach (~2 blocks)
    private static final float AXE_DAMAGE = 5.0F;    // stone-axe-ish hit
    private static final int ATTACK_COOLDOWN = 20;   // 1s between swings
    private static final int RETARGET_INTERVAL = 5;

    private final Villager villager;
    private LivingEntity target;
    private int attackCooldown;

    public StoneAxeDefenseGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean hurt() {
        return villager.getHealth() < villager.getMaxHealth() * HEALTH_FRAC;
    }

    /** Nearest living hostile (Enemy) that is NOT a creeper, in range and line of sight. */
    private LivingEntity findTarget() {
        AABB box = villager.getBoundingBox().inflate(SEE_RANGE);
        List<LivingEntity> candidates = villager.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && !(e instanceof Creeper) && e.isAlive() && e != villager
                        && villager.distanceToSqr(e) <= SEE_RANGE * SEE_RANGE
                        && villager.getSensing().hasLineOfSight(e));
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = villager.distanceToSqr(e);
            if (d < best) { best = d; nearest = e; }
        }
        return nearest;
    }

    @Override
    public boolean canUse() {
        if (!hurt()) return false;                                   // healthy -> flee normally
        if (villager.tickCount % RETARGET_INTERVAL != 0) return false;
        this.target = findTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return hurt() && target != null && target.isAlive()
                && villager.distanceToSqr(target) <= SEE_RANGE * SEE_RANGE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
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

        // Override the brain's flee while making this last stand (the goal only runs when hurt < 75%).
        Brain<?> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);

        villager.setTarget(t);
        villager.getLookControl().setLookAt(t, 30.0F, 30.0F);
        villager.getNavigation().moveTo(t, 1.1D);

        if (attackCooldown > 0) attackCooldown--;
        if (villager.distanceToSqr(t) <= REACH_SQR && attackCooldown <= 0) {
            villager.swing(InteractionHand.MAIN_HAND);
            t.hurt(villager.level().damageSources().mobAttack(villager), AXE_DAMAGE);
            attackCooldown = ATTACK_COOLDOWN;
        }
    }
}
