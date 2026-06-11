package com.example.villagergunners;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The wall defender: an immobile conduit-like turret creature. 100 HP (native LivingEntity health, so it
 * takes damage from arrows/explosions normally -- no custom handling), fires a real JEG gun once per second
 * at the nearest hostile (the same {@code Enemy} notion the villagers/guards use), dealing ~10 damage. It
 * never moves and ignores gravity/knockback, so it stays centred in its protecting-glass shell. Line of
 * sight is checked from just outside the shell so it can see (and shoot) through its own glass.
 */
public class WallDefenderEntity extends Mob {
    private static final double RANGE = 24.0;
    private static final int FIRE_INTERVAL = 20;     // one bullet per second (pre-stage-4 revolver)
    private static final int RETARGET_INTERVAL = 10; // re-scan for a target only every 0.5s (the heavy part)
    private static final ResourceLocation GUN_ID = new ResourceLocation("jeg", "revolver"); // base dmg 5
    private static final float GUN_BONUS_DAMAGE = 5.0F; // + base 5 = ~10 damage per shot

    // Stage 4: the defender fires our native rocket (the same RocketEntity the iron golems use), NOT a JEG
    // bullet. Rockets only fly beyond MIN_ROCKET_RANGE so the radius-5 blast can never reach our own glass
    // shell; a target closer than that is plinked with the revolver instead (so a point-blank attacker is
    // still engaged without the turret blowing itself up).
    private static final int ROCKET_INTERVAL = 40;    // one rocket per 2s at stage 4
    private static final double MIN_ROCKET_RANGE = 8.0; // > blast radius (+ shell clearance) -> never self-blast
    private static final double ROCKET_SPEED = 2.5;   // blocks/tick: fast, near-straight flight (matches golems)

    private static ItemStack gun; // lazily built once JEG's item registry is available
    private int fireCooldown;
    private int retargetCooldown;
    private boolean rocketMode;   // our village is at stage 4 -> fire rockets (refreshed on each re-target)
    private LivingEntity cachedTarget;

    public WallDefenderEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    private static ItemStack gun() {
        if (gun == null) gun = GunUtil.makeGun(GUN_ID, GUN_BONUS_DAMAGE);
        return gun;
    }

    @Override
    protected void registerGoals() {
        // No goals: it's a stationary turret driven entirely by aiStep().
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (fireCooldown > 0) fireCooldown--;
        if (retargetCooldown > 0) retargetCooldown--;

        // Re-acquire (the heavy entity scan + per-candidate LOS raytraces) only every RETARGET_INTERVAL
        // ticks, or as soon as the cached target dies/leaves range -- NOT every tick. Aiming at the cached
        // target stays per-tick because that's just a cheap rotation.
        if (retargetCooldown <= 0 || !isValidTarget(cachedTarget)) {
            cachedTarget = acquireTarget();
            rocketMode = isStage4();                   // refresh the stage gate alongside the (throttled) re-scan
            retargetCooldown = RETARGET_INTERVAL;
        }
        setTarget(cachedTarget);
        if (cachedTarget == null) return;
        GunUtil.aimAt(this, cachedTarget);            // face the target (JEG fires along body rotation; the rocket aims by hand)
        if (fireCooldown > 0) return;

        // Stage 4: rocket distant targets like the iron golems do; closer than the safe blast range, fall back
        // to the revolver so the immobile turret still engages a point-blank attacker without self-blasting.
        if (rocketMode && distanceToSqr(cachedTarget) >= MIN_ROCKET_RANGE * MIN_ROCKET_RANGE) {
            fireRocket(cachedTarget);
            fireCooldown = ROCKET_INTERVAL;
        } else if (!gun().isEmpty()) {
            GunUtil.fire(this, cachedTarget, gun(), 0.0F);  // real JEG bullet, perfect precision
            fireCooldown = FIRE_INTERVAL;
        }
    }

    /** Spawn a native rocket (our local clone, NOT JEG) aimed straight at the target. Mirrors GolemRocketGoal. */
    private void fireRocket(LivingEntity target) {
        RocketEntity rocket = new RocketEntity(level(), this);
        Vec3 from = rocket.position();
        Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize();
        rocket.setDeltaMovement(dir.scale(ROCKET_SPEED));
        rocket.setYRot((float) (Math.atan2(dir.x, dir.z) * (180.0 / Math.PI)));
        rocket.setXRot((float) (Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
        level().addFreshEntity(rocket);
        level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    /** True when the nearest Village Gravemind (this defender's village) has reached stage 4. */
    private boolean isStage4() {
        if (!(level() instanceof ServerLevel sl)) return false;
        GravemindData data = GravemindData.get(sl.getServer());
        GlobalPos nearest = null;
        double best = Double.MAX_VALUE;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            double d = gp.pos().distSqr(blockPosition());
            if (d < best) { best = d; nearest = gp; }
        }
        return nearest != null && data.econ(nearest).getLevel() >= 4;
    }

    /** Cheap per-tick check (no raytrace) so we can keep a target between full re-acquisitions. */
    private boolean isValidTarget(LivingEntity t) {
        return t != null && t.isAlive() && distanceToSqr(t) <= RANGE * RANGE;
    }

    private LivingEntity acquireTarget() {
        Vec3 eye = getEyePosition();
        // A player who attacked the village (or this defender) is the priority target -- same as the
        // villagers/guards (the gravemind's anti-grief aggro reaches us now that we're an "ally").
        net.minecraft.world.entity.player.Player aggro = GunnerEvents.getAggroPlayer(this);
        if (aggro != null && aggro.isAlive() && distanceToSqr(aggro) <= RANGE * RANGE
                && canShootThroughShell(eye, aggro)) {
            return aggro;
        }
        AABB box = getBoundingBox().inflate(RANGE);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                x -> x instanceof Enemy && x.isAlive() && x != this)) {
            double d = distanceToSqr(e);
            if (d > RANGE * RANGE || d >= bestD) continue;
            if (!canShootThroughShell(eye, e)) continue;
            best = e;
            bestD = d;
        }
        return best;
    }

    /** LOS that ignores our own shell by starting ~2 blocks out (past the 1-thick protecting glass). */
    private boolean canShootThroughShell(Vec3 from, LivingEntity t) {
        Vec3 te = t.getEyePosition();
        Vec3 dir = te.subtract(from);
        double len = dir.length();
        if (len < 2.0) return true;
        Vec3 start = from.add(dir.scale(2.0 / len));
        return level().clip(new ClipContext(start, te,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // --- immobile turret: never translate, never get pushed or knocked back ---
    @Override public void travel(Vec3 wanted) { /* anchored */ }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
}
