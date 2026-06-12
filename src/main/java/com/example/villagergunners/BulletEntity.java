package com.example.villagergunners;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
import net.minecraftforge.network.NetworkHooks;
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
    private static final DustParticleOptions TRAIL =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.0F), 0.6F); // trailColor -256 = yellow

    private float damage = DEFAULT_DAMAGE; // base damage (before headshot/falloff); set by the shooter

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level);
    }

    public BulletEntity(Level level, LivingEntity shooter) {
        super(ModEntities.BULLET.get(), shooter, level); // spawns at the shooter's eye
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected float getGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        Vec3 before = position();
        super.tick();
        if (level() instanceof ServerLevel sl && isAlive()) {
            // bright yellow tracer along the path travelled this tick (the bullet itself is invisible)
            Vec3 seg = position().subtract(before);
            int steps = Math.max(1, (int) (seg.length() / 0.5));
            for (int i = 0; i < steps; i++) {
                Vec3 p = before.add(seg.scale((i + 0.5) / steps));
                sl.sendParticles(TRAIL, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (!level().isClientSide() && tickCount > LIFE) discard();
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

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** Aim a fresh bullet from {@code shooter}'s eyes at {@code target}'s centre and launch it (JEG-style). */
    public static void fire(Mob shooter, LivingEntity target, float damage) {
        if (shooter.level().isClientSide()) return;
        BulletEntity bullet = new BulletEntity(shooter.level(), shooter);
        bullet.damage = damage;
        Vec3 from = bullet.position();
        Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize();
        bullet.setDeltaMovement(dir.scale(SPEED));
        bullet.setYRot((float) (Math.atan2(dir.x, dir.z) * (180.0 / Math.PI)));
        bullet.setXRot((float) (Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
        shooter.level().addFreshEntity(bullet);
        shooter.level().playSound(null, shooter.getX(), shooter.getY() + shooter.getEyeHeight(), shooter.getZ(),
                ModSounds.REVOLVER_FIRE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
