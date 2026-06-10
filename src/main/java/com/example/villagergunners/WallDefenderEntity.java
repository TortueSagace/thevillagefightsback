package com.example.villagergunners;

import net.minecraft.resources.ResourceLocation;
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
    private static final int FIRE_INTERVAL = 20;     // one shot per second
    private static final ResourceLocation GUN_ID = new ResourceLocation("jeg", "revolver"); // base dmg 5
    private static final float GUN_BONUS_DAMAGE = 5.0F; // + base 5 = ~10 damage per shot

    private static ItemStack gun; // lazily built once JEG's item registry is available
    private int fireCooldown;

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
        LivingEntity target = acquireTarget();
        setTarget(target);
        if (target == null) return;
        GunUtil.aimAt(this, target);                  // JEG fires along body rotation, so face the target first
        if (fireCooldown <= 0 && !gun().isEmpty()) {
            GunUtil.fire(this, target, gun(), 0.0F);  // real JEG bullet, perfect precision
            fireCooldown = FIRE_INTERVAL;
        }
    }

    private LivingEntity acquireTarget() {
        Vec3 eye = getEyePosition();
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
