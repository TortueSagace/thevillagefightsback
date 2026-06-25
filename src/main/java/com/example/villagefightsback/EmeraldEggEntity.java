package com.example.villagefightsback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Egg's thrown projectile: hurled like a snowball, it inflicts {@link EmeraldVirus} I on whatever
 * creature it strikes (so a hit unit that dies carrying it spawns an emerald mite). It bursts into a small green
 * shower on any impact. Emerald chickens lay these eggs.
 */
public class EmeraldEggEntity extends ThrowableItemProjectile {
    public EmeraldEggEntity(EntityType<? extends EmeraldEggEntity> type, Level level) {
        super(type, level);
    }

    public EmeraldEggEntity(Level level, LivingEntity thrower) {
        super(ModEntities.EMERALD_EGG.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.EMERALD_EGG.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide() && result.getEntity() instanceof LivingEntity victim) {
            EmeraldVirus.infect(victim, 0); // Emerald Virus I -> spawns a mite on death (gated by enable_mob_infection)
            if (getOwner() instanceof net.minecraft.server.level.ServerPlayer thrower) {
                Achievements.award(thrower, "brain_node/a_very_bad_speech"); // landed an emerald egg on a target
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result); // dispatches to onHitEntity / onHitBlock
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.7F, 1.4F);
            discard();
        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
