package com.example.villagergunners;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * A native rocket projectile -- a clone of JEG's rocket mechanism so the iron golem's rocket launcher no
 * longer needs JEG at runtime. It flies dead-straight (no gravity), deals a heavy direct hit, and detonates
 * on contact with a TNT-strength, block-breaking explosion that is attributed to its OWNER (the golem), so
 * the mod's ally-immunity in {@code GunnerEvents.onLivingHurt} still spares villagers/golems caught in it.
 */
public class RocketEntity extends ThrowableProjectile implements ItemSupplier {
    public static final float DIRECT_DAMAGE = 28.0F;     // matches JEG's rocket projectile damage
    public static final float EXPLOSION_RADIUS = 5.0F;   // matches JEG's default missile blast
    private static final int MAX_LIFETIME = 200;         // self-destruct if it never hits anything (~10s)

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public RocketEntity(Level level, LivingEntity shooter) {
        super(ModEntities.ROCKET.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        // No synced data: it's always a rocket and its velocity rides the spawn packet.
    }

    /** Dead-straight flight (the golem aims directly at the target). */
    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        long _p = ModProfiler.now();
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            // Firework-style trail: smoke + sparks, interpolated along this tick's path (it moves fast).
            net.minecraft.world.phys.Vec3 now = position();
            net.minecraft.world.phys.Vec3 prev = new net.minecraft.world.phys.Vec3(xOld, yOld, zOld);
            int steps = 3;
            for (int i = 1; i <= steps; i++) {
                net.minecraft.world.phys.Vec3 p = prev.lerp(now, (double) i / steps);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.02);
            }
        }
        if (!level().isClientSide() && tickCount > MAX_LIFETIME) {
            discard();
        }
        ModProfiler.add("rocket", _p);
    }

    /** Direct impact: a heavy hit on the struck entity, attributed to the golem (owner). */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide()) return;
        Entity owner = getOwner();
        result.getEntity().hurt(
                damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null),
                DIRECT_DAMAGE);
    }

    /** Any contact (block or entity): detonate, then vanish. */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide()) return;
        Entity owner = getOwner();
        // TNT-style, block-breaking blast (mobGriefing-gated since the source is the golem); no extra fire.
        level().explode(owner, getX(), getY(), getZ(), EXPLOSION_RADIUS, false, Level.ExplosionInteraction.TNT);
        discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.ROCKET.get()); // the billboard the client renders
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this); // carry velocity to the client
    }
}
