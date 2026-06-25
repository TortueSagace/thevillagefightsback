package com.example.villagefightsback;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A native bullet -- a JEG-free clone of the revolver round, so the wall defender no longer needs JEG at
 * runtime. It mirrors jeg:revolver's projectile config: base damage 5 (x1.5 headshot on the upper quarter of
 * a target) with falloff over the bullet's life, gravity -0.04/tick, speed 10 blocks/tick, 40-tick life, a
 * tiny hitbox, and a bright yellow tracer (trailColor 0xFFFF00). Hits are attributed to the shooter so the
 * mod's ally-immunity still spares villagers/golems.
 */
public class BulletEntity extends ThrowableProjectile {
    public static final float DEFAULT_DAMAGE = 5.0F;   // jeg:revolver projectile.damage
    private static final float HEADSHOT = 1.5F;        // jeg:revolver projectile.headshotMultiplier
    public static final double SPEED = 10.0;           // jeg:revolver projectile.speed (blocks/tick)
    private static final int LIFE = 40;                // jeg:revolver projectile.life
    private static final float GRAVITY = 0.04F;        // jeg modifiedProjectileGravity
    // A fat yellow dust core for colour + an END_ROD for the fullbright glow (dust is dimmed by darkness; the
    // END_ROD glows in the dark). Both are sent force=true so the tracer is visible from far away, not just 32m.
    private static final DustParticleOptions TRAIL =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.2F), 1.6F); // big bright yellow

    private float damage = DEFAULT_DAMAGE; // base damage (before headshot/falloff); set by the shooter

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level);
    }

    public BulletEntity(Level level, LivingEntity shooter) {
        super(ModEntities.BULLET.get(), shooter, level); // spawns at the shooter's eye
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        Vec3 before = position();
        super.tick();
        if (level() instanceof ServerLevel sl && isAlive()) {
            // A glowing tracer along the path travelled this tick (the bullet itself is invisible): a fat yellow
            // dust core + an END_ROD that stays bright in the dark, both forced so it reads from far away.
            Vec3 seg = position().subtract(before);
            int steps = Math.max(1, (int) (seg.length() / 0.4));
            for (int i = 0; i < steps; i++) {
                Vec3 p = before.add(seg.scale((i + 0.5) / steps));
                forced(sl, TRAIL, p);
                forced(sl, net.minecraft.core.particles.ParticleTypes.END_ROD, p);
            }
        }
        if (!level().isClientSide() && tickCount > LIFE) discard();
    }

    /** Send one particle to every player in the level with force=true, so the tracer shows past the normal
     *  ~32-block particle cutoff (force = 512-block range) and is visible from far away. */
    private static void forced(ServerLevel sl, net.minecraft.core.particles.ParticleOptions type, Vec3 p) {
        for (net.minecraft.server.level.ServerPlayer pl : sl.players()) {
            sl.sendParticles(pl, type, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide()) return;
        Entity target = result.getEntity();
        float dmg = damage * Math.max(0.0F, (float) (LIFE - tickCount) / LIFE); // falloff over the bullet's life
        double frac = (result.getLocation().y - target.getY()) / Math.max(0.1, target.getBbHeight());
        if (frac >= 0.75) dmg *= HEADSHOT; // headshot: hit the upper quarter
        Entity owner = getOwner();
        target.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null), dmg);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide()) discard();
    }

    // getAddEntityPacket override deleted: modded entities use the vanilla spawn packet automatically in NeoForge.

    /** Aim a fresh bullet from {@code shooter}'s eyes at {@code target}'s centre and launch it (JEG-style). */
    public static void fire(Mob shooter, LivingEntity target, float damage) {
        fireFrom(shooter, shooter.getEyePosition(), target, damage);
    }

    /** Like {@link #fire} but launched from an explicit {@code origin} (e.g. a turret head higher than the
     *  shooter's eyes, so the visible tracer leaves the head, not the chest). */
    public static void fireFrom(Mob shooter, Vec3 origin, LivingEntity target, float damage) {
        if (shooter.level().isClientSide()) return;
        BulletEntity bullet = new BulletEntity(shooter.level(), shooter);
        bullet.setPos(origin.x, origin.y, origin.z);
        bullet.damage = damage;
        Vec3 dir = target.getBoundingBox().getCenter().subtract(origin).normalize();
        bullet.setDeltaMovement(dir.scale(SPEED));
        bullet.setYRot((float) (Math.atan2(dir.x, dir.z) * (180.0 / Math.PI)));
        bullet.setXRot((float) (Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
        shooter.level().addFreshEntity(bullet);
        shooter.level().playSound(null, origin.x, origin.y, origin.z,
                ModSounds.REVOLVER_FIRE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
