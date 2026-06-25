package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.VolatileLimboPotionEntity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Limbo Sage's signature defence: on a long cooldown, lob a Volatile Limbo Potion at the nearest BIG hostile
 * (a monster with more than 20 max health) within range and line of sight, banishing it (and anything caught in
 * the burst, except the sage). The throw mirrors {@code LimboWitchEntity.performRangedAttack} (arc + speed).
 *
 * <p>The sage never targets village allies: the candidate scan only accepts {@link Enemy} monsters, and never a
 * {@link Villager}, {@link IronGolem}, or {@link Player}. (The sage is a peaceful profession villager; this is
 * its only offensive behaviour.)</p>
 */
public class SageVolatileThrowGoal extends Goal {
    private static final double RANGE = 12.0;          // throw range (blocks)
    private static final double MIN_HEALTH = 20.0;     // only worth it on a BIG threat (max health > this)
    private static final int COOLDOWN = 1200;          // ticks between throws (60s)
    private static final int RESCAN_INTERVAL = 20;     // ticks between (throttled) target scans

    private final Villager sage;
    private int cooldown;
    private LivingEntity target;

    public SageVolatileThrowGoal(Villager sage) {
        this.sage = sage;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    /** A valid throw target: a living, in-range, big monster the sage can see -- never an ally. */
    private boolean isValidTarget(LivingEntity t) {
        if (t == null || !t.isAlive()) return false;
        if (!(t instanceof Enemy)) return false;                 // only hostiles (vanilla monsters / raiders)
        if (t instanceof Villager || t instanceof IronGolem || t instanceof Player) return false; // never allies
        if (com.example.villagefightsback.Factions.isSparedByVillage(t)) return false; // never Darius or the friendly Vindictive Village Spirit
        if (t.getMaxHealth() <= MIN_HEALTH) return false;        // only the big threats
        if (sage.distanceToSqr(t) > RANGE * RANGE) return false;
        return sage.getSensing().hasLineOfSight(t);
    }

    /** Self-scan for the nearest valid target (mirrors GolemRocketGoal's self-scan). */
    private LivingEntity findTarget() {
        AABB box = sage.getBoundingBox().inflate(RANGE);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : sage.getLevel().getEntitiesOfClass(LivingEntity.class, box, this::isValidTarget)) {
            double d = sage.distanceToSqr(e);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (sage.tickCount % RESCAN_INTERVAL != 0) return false; // throttle the (bounded) entity query
        target = findTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return false; // a single throw per activation; the cooldown gates the next one
    }

    @Override
    public void start() {
        if (target == null) return;
        sage.getLookControl().setLookAt(target, 30.0F, 30.0F);
        throwVolatilePotion(target);
        cooldown = COOLDOWN;
    }

    /** Lob a Volatile Limbo Potion at the target, arcing exactly like a witch's potion throw. */
    private void throwVolatilePotion(LivingEntity t) {
        Vec3 v = t.getDeltaMovement();
        double dx = t.getX() + v.x - sage.getX();
        double dy = t.getEyeY() - 1.1 - sage.getY();
        double dz = t.getZ() + v.z - sage.getZ();
        double dh = Math.sqrt(dx * dx + dz * dz);
        VolatileLimboPotionEntity pot = new VolatileLimboPotionEntity(sage.getLevel(), sage);
        pot.setXRot(pot.getXRot() - 20.0F);
        pot.shoot(dx, dy + dh * 0.2, dz, 0.75F, 8.0F);
        if (!sage.isSilent()) {
            sage.getLevel().playSound((Player) null, sage.getX(), sage.getY(), sage.getZ(),
                    SoundEvents.WITCH_THROW, SoundSource.NEUTRAL,
                    1.0F, 0.8F + sage.getRandom().nextFloat() * 0.4F);
        }
        sage.getLevel().addFreshEntity(pot);
    }
}
