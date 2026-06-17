package com.example.villagefightsback.goal;

import java.util.EnumSet;
import java.util.List;

import com.example.villagefightsback.VillageEvents;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Nerfed (un-upgraded) villager combat: the villager actively defends, meleeing the nearest hostile (NEVER a
 * creeper) and any player who attacked the village, with its stone axe. It is nerfed in POWER (low damage, no
 * armour), not in courage -- it stands and fights threats near its village instead of cowering. It still will
 * not leave its wall to chase ({@link VillageEvents#beyondHomeWall}).
 */
public class StoneAxeDefenseGoal extends Goal {
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

    /** A player who attacked the village (retaliation) takes priority, else the nearest hostile. */
    private LivingEntity acquireTarget() {
        Player aggro = VillageEvents.getAggroPlayer(villager);
        if (aggro != null && inRange(aggro)) return aggro;
        return findTarget();
    }

    private boolean inRange(LivingEntity t) {
        return t != null && t.isAlive()
                && villager.distanceToSqr(t) <= SEE_RANGE * SEE_RANGE
                && villager.getSensing().hasLineOfSight(t);
    }

    /** Nearest living hostile (Enemy) that is NOT a creeper, in range and line of sight. */
    private LivingEntity findTarget() {
        AABB box = villager.getBoundingBox().inflate(SEE_RANGE);
        List<LivingEntity> candidates = villager.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> (e instanceof Enemy || com.example.villagefightsback.Factions.isInfected(e))
                        && !com.example.villagefightsback.Factions.isSparedByVillage(e) // never Darius or the friendly Vindictive Village Spirit
                        && !(e instanceof Creeper) && e.isAlive() && e != villager
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
        if (villager.tickCount % RETARGET_INTERVAL != 0) return false;
        this.target = acquireTarget();
        if (VillageEvents.beyondHomeWall(villager, this.target)) this.target = null; // don't leave our wall to chase it
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return inRange(this.target) && !VillageEvents.beyondHomeWall(villager, this.target); // stop if it leaves our wall
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        // A villager that was just building is bare-handed (WallBuildGoal empties the slot when it stops);
        // re-arm with the stone axe before defending so it actually swings a weapon.
        VillageEvents.restoreDefenderWeapon(villager);
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

        // Suppress the villager's vanilla flee/panic so it stands and fights any threat in range -- including
        // an enemy that broke INSIDE the wall (beyondHomeWall is false for an in-wall target). Mirrors the
        // upgraded AxeCombatGoal (which also erases HURT_BY_ENTITY).
        Brain<?> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);

        villager.setTarget(t);
        villager.getLookControl().setLookAt(t, 30.0F, 30.0F);
        com.example.villagefightsback.NavThrottle.follow(villager, t, 1.1D); // throttled repath

        if (attackCooldown > 0) attackCooldown--;
        if (villager.distanceToSqr(t) <= REACH_SQR && attackCooldown <= 0) {
            villager.swing(InteractionHand.MAIN_HAND);
            t.hurt(villager.level().damageSources().mobAttack(villager), AXE_DAMAGE);
            attackCooldown = ATTACK_COOLDOWN;
        }
    }
}
