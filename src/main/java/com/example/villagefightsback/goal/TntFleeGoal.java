package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.CombatUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * "Get off the infection" reflex for villagers. When a villager is standing on a block from From Another
 * World (any {@value CombatUtil#FAW_NAMESPACE} block -- e.g. assimilated/"converted" sculk, gore, tentacles),
 * it primes a TNT at its feet and flees the spot for {@link #FLEE_TICKS} ticks. It also alerts every
 * villager within {@link #ALERT_RADIUS} blocks to flee the same spot -- unless they are mid-fight.
 *
 * <p>Flee state lives in NBT (danger point + deadline) so one villager can set it on its neighbours; each
 * villager's own goal instance then drives its own retreat from that point. Runs at a higher priority than
 * the gun-combat goal so the retreat preempts holding ground (the combat goal, while preempted, stops
 * wiping the WALK_TARGET we set). FAW is not a dependency -- detection is by namespace, so this no-ops
 * cleanly when FAW is absent.
 */
public class TntFleeGoal extends Goal {
    // NBT keys -- shared so a triggering villager can write flee state onto its neighbours.
    public static final String NBT_FLEE_UNTIL = "vgun_flee_until";
    public static final String NBT_FLEE_X = "vgun_flee_x";
    public static final String NBT_FLEE_Y = "vgun_flee_y";
    public static final String NBT_FLEE_Z = "vgun_flee_z";
    public static final String NBT_TNT_CD = "vgun_tnt_cd_until";

    private static final int FLEE_TICKS = 60;        // flee for 3 seconds
    private static final int TNT_FUSE = 70;          // TNT life before it detonates (~3.5s)
    private static final int TNT_COOLDOWN = 100;     // min ticks before this villager primes TNT again
    private static final double ALERT_RADIUS = 5.0;  // nearby villagers told to flee the same spot
    private static final int SCAN_INTERVAL = 5;      // ticks between "am I on FAW?" checks
    private static final float FLEE_SPEED = 0.8F;    // brisk retreat
    private static final int FLEE_PICK_RADIUS = 14;  // how far ahead to pick a retreat point

    private final Villager villager;
    private long nextScan = Long.MIN_VALUE;
    private Vec3 fleeTarget;

    public TntFleeGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE)); // claim MOVE so the gun-combat goal yields while we flee
    }

    private CompoundTag data() {
        return villager.getPersistentData();
    }

    private long fleeUntil() {
        return data().getLong(NBT_FLEE_UNTIL);
    }

    @Override
    public boolean canUse() {
        long now = villager.level.getGameTime();
        if (now < fleeUntil()) return true;          // flee state already set (by us or a neighbour)
        if (now < nextScan) return false;            // throttle the block check
        nextScan = now + SCAN_INTERVAL;
        if (now >= data().getLong(NBT_TNT_CD) && CombatUtil.isOnFawBlock(villager)) {
            triggerTnt(now);
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return villager.level.getGameTime() < fleeUntil();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        fleeTarget = null;
    }

    @Override
    public void stop() {
        fleeTarget = null;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    public void tick() {
        Vec3 danger = new Vec3(
                data().getDouble(NBT_FLEE_X), data().getDouble(NBT_FLEE_Y), data().getDouble(NBT_FLEE_Z));
        // Pick a retreat point away from the danger; repick once we have reached (or lost) it.
        if (fleeTarget == null || villager.distanceToSqr(fleeTarget) < 4.0) {
            Vec3 away = DefaultRandomPos.getPosAway(villager, FLEE_PICK_RADIUS, 7, danger);
            if (away != null) fleeTarget = away;
        }
        if (fleeTarget != null) {
            // Villagers are brain-driven: drive the retreat through the brain's WALK_TARGET. The combat
            // goal (which would otherwise wipe this) is preempted by our MOVE flag while we run.
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(fleeTarget, FLEE_SPEED, 0));
        }
    }

    /** Prime a TNT under this villager, flee the spot, and tell nearby non-fighting villagers to flee too. */
    private void triggerTnt(long now) {
        CombatUtil.primeTnt(villager, TNT_FUSE);
        Vec3 danger = villager.position();
        long until = now + FLEE_TICKS;
        setFlee(villager, until, danger);
        data().putLong(NBT_TNT_CD, now + TNT_COOLDOWN);

        AABB box = villager.getBoundingBox().inflate(ALERT_RADIUS);
        for (Villager other : villager.level.getEntitiesOfClass(Villager.class, box)) {
            if (other == villager || isFighting(other)) continue; // fighters hold their ground
            setFlee(other, until, danger);
        }
    }

    private static void setFlee(Villager v, long until, Vec3 danger) {
        CompoundTag d = v.getPersistentData();
        d.putLong(NBT_FLEE_UNTIL, until);
        d.putDouble(NBT_FLEE_X, danger.x);
        d.putDouble(NBT_FLEE_Y, danger.y);
        d.putDouble(NBT_FLEE_Z, danger.z);
    }

    /** A villager is "fighting" if it currently has a live hostile target. */
    private static boolean isFighting(Villager v) {
        LivingEntity tgt = v.getTarget();
        return tgt != null && tgt.isAlive()
                && !com.example.villagefightsback.Factions.isSparedByVillage(tgt) // Darius / the friendly spirit are allies, not foes to "fight"
                && com.example.villagefightsback.Factions.isVillageThreat(tgt);
    }
}
