package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.GunUtil;
import com.example.villagergunners.GunnerEvents;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Villager combat behaviour: hold ground, face the hostile, and shoot it with the revolver.
 * When the hostile is within {@link #FLAME_RANGE} blocks, switch to the flamethrower instead.
 * A hostile is "seen" when it is within {@link #SEE_RANGE} blocks AND in direct line of sight.
 * If a player recently struck the villager, that player becomes the target (retaliation).
 */
public class VillagerGunCombatGoal extends Goal {
    private static final double SEE_RANGE = 30.0;
    // Weapon-switch dead band (squared): engage flamethrower at <=5 blocks, fall back to revolver
    // only past 6.5 blocks, so a target hovering near the boundary doesn't flicker the held item.
    private static final double FLAME_ON_SQR = 5.0 * 5.0;    // 25.0
    private static final double FLAME_OFF_SQR = 6.5 * 6.5;   // 42.25
    private static final int RETARGET_INTERVAL = 5; // ticks between target re-scans

    private final Villager villager;
    private final ItemStack revolver;
    private final ItemStack flamethrower;
    private final int revolverRate;
    private final int flameRate;

    private LivingEntity target;
    private int revolverCooldown;
    private int flameCooldown;
    private boolean usingFlame; // current weapon choice, with hysteresis (not an equipped item)

    public VillagerGunCombatGoal(Villager villager) {
        this.villager = villager;
        this.revolver = GunUtil.makeGun(GunUtil.REVOLVER);
        this.flamethrower = GunUtil.makeGun(GunUtil.FLAMETHROWER);
        // Floor the fire rates so a mob isn't unreasonably fast; flamethrower stays near-continuous.
        this.revolverRate = Math.max(6, GunUtil.fireRate(revolver, 6));
        this.flameRate = Math.max(2, GunUtil.fireRate(flamethrower, 2));
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private LivingEntity acquireTarget() {
        Player aggro = GunnerEvents.getAggroPlayer(villager);
        if (aggro != null && inSight(aggro)) return aggro;
        return GunUtil.findNearestHostile(villager, SEE_RANGE);
    }

    private boolean inSight(LivingEntity t) {
        return t != null && t.isAlive()
                && villager.distanceToSqr(t) <= SEE_RANGE * SEE_RANGE
                && villager.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canUse() {
        if (revolver.isEmpty() && flamethrower.isEmpty()) return false;
        if (villager.tickCount % RETARGET_INTERVAL != 0) return false; // throttle scans
        this.target = acquireTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return inSight(this.target);
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
    }

    @Override
    public void tick() {
        LivingEntity t = this.target;
        if (t == null) return;

        // Stand and fight: hold ground and face the target, overriding the villager's flee behaviour.
        // Villagers are brain-driven; the brain's panic behaviour re-writes WALK_TARGET every tick.
        // This goal's tick() runs BEFORE the brain ticks, so we erase the panic *trigger* memories
        // here -- with no HURT_BY/hostile memory present, the panic behaviour won't activate and
        // won't issue a flee path this tick. (We track our own retaliation target via NBT, so
        // erasing HURT_BY doesn't affect the mod's combat.)
        Brain<?> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
        villager.getNavigation().stop();
        villager.getLookControl().setLookAt(t, 30.0F, 30.0F);
        villager.setTarget(t);

        if (revolverCooldown > 0) revolverCooldown--;
        if (flameCooldown > 0) flameCooldown--;

        // Choose the weapon by distance, with a dead band so the choice doesn't chatter at 5 blocks.
        double dSqr = villager.distanceToSqr(t);
        usingFlame = usingFlame ? (dSqr <= FLAME_OFF_SQR) : (dSqr <= FLAME_ON_SQR);
        if (usingFlame && !flamethrower.isEmpty()) {
            if (flameCooldown <= 0) {
                GunUtil.aimAt(villager, t);
                GunUtil.fire(villager, t, flamethrower, 0.0F); // 0 spread = perfect precision
                flameCooldown = flameRate;
            }
        } else if (!revolver.isEmpty()) {
            if (revolverCooldown <= 0) {
                GunUtil.aimAt(villager, t);
                GunUtil.fire(villager, t, revolver, 0.0F); // 0 spread = perfect precision
                revolverCooldown = revolverRate;
            }
        }
    }
}
