package com.example.villagefightsback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A NATIVE Wind Charge projectile -- an IDENTICAL clone of the vanilla 1.21 wind charge (so the mod no longer
 * needs any third-party weapon mod). It flies dead-straight (no gravity, no drag), deals a token 1.0 hit on
 * a direct entity strike, and on ANY contact bursts into a knockback-only gust: it shoves nearby living
 * entities away from the burst centre (horizontal + a little up) with NO block or explosion damage, then
 * vanishes. The owner is immune. Billboards its item sprite via {@link ItemSupplier}.
 */
public class WindChargeEntity extends AbstractHurtingProjectile implements ItemSupplier {
    private static final float DIRECT_DAMAGE = 1.0F;
    private static final double BURST_RADIUS = 1.5;
    private static final int MAX_LIFETIME = 200; // self-destruct if it never hits anything (~10s)

    public WindChargeEntity(EntityType<? extends WindChargeEntity> type, Level level) {
        super(type, level);
    }

    public WindChargeEntity(Level level, LivingEntity owner) {
        super(ModEntities.WIND_CHARGE.get(), level);
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
    }

    /** Flies straight: no gravity (inertia 1.0) and no fiery trail. */
    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected net.minecraft.core.particles.ParticleOptions getTrailParticle() {
        return ParticleTypes.CLOUD; // a faint wind contrail (1.20.1 has no dedicated "gust" particle)
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > MAX_LIFETIME) {
            discard();
        }
    }

    /** Direct hit: a token 1.0 damage (the gust knockback is the real effect, applied in {@link #onHit}). */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide()) return;
        Entity owner = getOwner();
        result.getEntity().hurt(
                damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null), DIRECT_DAMAGE);
    }

    /** Any contact: a knockback-only wind burst -- shove nearby living entities away, then vanish. */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel sl)) return;
        double x = getX(), y = getY(), z = getZ();
        Entity owner = getOwner();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x, y, z, x, y, z).inflate(BURST_RADIUS), LivingEntity::isAlive)) {
            if (e == owner) continue;
            Vec3 push = e.position().subtract(x, y, z);
            double dist = push.length();
            Vec3 dir = dist > 1.0E-4 ? push.scale(1.0 / dist) : new Vec3(0, 1, 0);
            double power = (BURST_RADIUS - Math.min(dist, BURST_RADIUS)) / BURST_RADIUS + 0.2; // stronger up close
            e.push(dir.x * power, 0.35 + power * 0.4, dir.z * power);
            e.hurtMarked = true; // resync the new velocity to clients
        }
        sl.sendParticles(ParticleTypes.POOF, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles(ParticleTypes.CLOUD, x, y, z, 12, 0.4, 0.4, 0.4, 0.05);
        sl.playSound(null, x, y, z, SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 1.0F, 1.4F);
        discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.WIND_CHARGE.get()); // the billboard the client renders
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this); // carry velocity to the client
    }
}
