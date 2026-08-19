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
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * The Volatile Limbo Potion's thrown projectile. On impact it bursts and banishes every living creature in a
 * splash radius to Limbo (the thrower is spared, so it reads as a weapon, not a suicide pact). It is NOT
 * lingering -- the banish is instantaneous and there is no cloud left behind.
 */
public class VolatileLimboPotionEntity extends ThrowableItemProjectile {
    private static final double SPLASH_RADIUS = 4.0;

    public VolatileLimboPotionEntity(EntityType<? extends VolatileLimboPotionEntity> type, Level level) {
        super(type, level);
    }

    public VolatileLimboPotionEntity(Level level, LivingEntity thrower) {
        super(ModEntities.VOLATILE_LIMBO_POTION.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.VOLATILE_LIMBO_POTION.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel sl)) return;
        double x = getX(), y = getY(), z = getZ();
        // the burst (soul + portal swirl, like a small banishment)
        sl.sendParticles(ParticleTypes.SOUL, x, y, z, 40, 1.4, 0.8, 1.4, 0.05);
        sl.sendParticles(ParticleTypes.PORTAL, x, y, z, 60, 1.6, 1.0, 1.6, 0.4);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 1.2, 0.8, 1.2, 0.02);
        sl.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.2F, 0.6F);

        Entity owner = getOwner();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x, y, z, x, y, z).inflate(SPLASH_RADIUS), LivingEntity::isAlive)) {
            if (e == owner) continue; // spare the thrower
            LimboDimension.sendToLimbo(e); // drops the gravel marker + plays the per-entity banish FX
        }
        discard();
    }

}
