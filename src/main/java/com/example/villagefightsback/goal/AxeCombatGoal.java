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
 * Upgraded villager melee combat with the Alien Slasher. Replaces the gun loadout at stage 3: the villager
 * hunts the nearest hostile (creepers excluded -- the huge knockback would just punt them mid-fuse) and
 * hacks it with the axe. The slash's payload (Emerald Virus, Slowness IV, fire, 20-block launch) is applied
 * by {@code EmeraldVirusEvents.onLivingHurt} since the villager holds the axe. A player who recently struck
 * the villager becomes the target instead (retaliation), mirroring the old gun behaviour.
 */
public class AxeCombatGoal extends Goal {
    private static final double SEE_RANGE = 24.0;     // detect hostiles within this
    private static final double REACH = 1.2;          // melee reach: gap beyond the body (size-independent)
    private static final float AXE_DAMAGE = 10.0F;    // netherite-axe damage
    private static final int ATTACK_COOLDOWN = 20;    // 1s between swings
    private static final int RETARGET_INTERVAL = 5;

    private final Villager villager;
    private LivingEntity target;
    private int attackCooldown;

    public AxeCombatGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private LivingEntity acquireTarget() {
        Player aggro = VillageEvents.getAggroPlayer(villager);
        if (aggro != null && inRange(aggro)) return aggro;
        return findHostile();
    }

    private boolean inRange(LivingEntity t) {
        return t != null && t.isAlive()
                && villager.distanceToSqr(t) <= SEE_RANGE * SEE_RANGE
                && villager.getSensing().hasLineOfSight(t);
    }

    /** Nearest living hostile (Enemy) that is NOT a creeper, in range and line of sight. */
    private LivingEntity findHostile() {
        AABB box = villager.getBoundingBox().inflate(SEE_RANGE);
        List<LivingEntity> candidates = villager.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> com.example.villagefightsback.Factions.isVillageThreat(e)
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
        long _p = com.example.villagefightsback.ModProfiler.now();
        try {
            this.target = acquireTarget();
            if (VillageEvents.beyondHomeWall(villager, this.target)) this.target = null; // don't leave our wall to chase it
            return target != null;
        } finally {
            com.example.villagefightsback.ModProfiler.add("ai.axe", _p);
        }
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
        // A villager that was just building is bare-handed (WallBuildGoal empties the slot when it stops).
        // Re-arm before slashing, so the slasher is both VISIBLE and lands its Emerald-Virus on-hit payload.
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

        // Villagers are brain-driven and would flee; erase the panic-trigger memories so they stand and fight.
        Brain<?> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);

        villager.setTarget(t);
        villager.getLookControl().setLookAt(t, 30.0F, 30.0F);
        com.example.villagefightsback.NavThrottle.follow(villager, t, 1.15D); // throttled repath (pathfinding was the #1 cost)

        if (attackCooldown > 0) attackCooldown--;
        if (inMeleeReach(t) && attackCooldown <= 0) {
            villager.swing(InteractionHand.MAIN_HAND);
            t.hurt(net.minecraft.world.damagesource.DamageSource.mobAttack(villager), AXE_DAMAGE);
            attackCooldown = ATTACK_COOLDOWN;
        }
    }

    /**
     * In melee reach if the GAP between the two bounding boxes is within {@link #REACH}. Using box-to-box
     * distance (not centre-to-centre) is size-independent: a giant scaled emeraldger can still hit a small
     * target standing at its feet, where a centre-to-centre check would read the big vertical offset as
     * "too far".
     */
    private boolean inMeleeReach(LivingEntity t) {
        AABB me = villager.getBoundingBox(), other = t.getBoundingBox();
        double dx = Math.max(0.0, Math.max(me.minX - other.maxX, other.minX - me.maxX));
        double dy = Math.max(0.0, Math.max(me.minY - other.maxY, other.minY - me.maxY));
        double dz = Math.max(0.0, Math.max(me.minZ - other.maxZ, other.minZ - me.maxZ));
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }
}
