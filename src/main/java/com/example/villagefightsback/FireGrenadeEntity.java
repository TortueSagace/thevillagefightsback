package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * The Fire Grenade's thrown projectile -- a native replacement for JEG's molotov. On impact it shatters into
 * a burst of flame: it sets every nearby creature on fire and (when mobGriefing is on) scatters fire onto the
 * ground around it, then vanishes.
 */
public class FireGrenadeEntity extends ThrowableItemProjectile {
    private static final double SPLASH_RADIUS = 3.0;
    private static final int FIRE_SECONDS = 5;

    public FireGrenadeEntity(EntityType<? extends FireGrenadeEntity> type, Level level) {
        super(type, level);
    }

    public FireGrenadeEntity(Level level, LivingEntity thrower) {
        super(ModEntities.FIRE_GRENADE.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.FIRE_GRENADE.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel sl)) return;
        double x = getX(), y = getY(), z = getZ();
        // the burst
        sl.sendParticles(ParticleTypes.FLAME, x, y, z, 70, 1.6, 1.0, 1.6, 0.06);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 25, 1.3, 1.0, 1.3, 0.02);
        sl.sendParticles(ParticleTypes.LAVA, x, y, z, 10, 0.9, 0.5, 0.9, 0.0);
        sl.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2F, 0.9F);
        sl.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.5F, 1.6F);

        // set nearby creatures alight (not the thrower)
        Entity owner = getOwner();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(x, y, z, x, y, z).inflate(SPLASH_RADIUS), LivingEntity::isAlive)) {
            if (e == owner) continue;
            e.setSecondsOnFire(FIRE_SECONDS);
        }

        // scatter fire onto solid ground (mobGriefing-gated, like real fire spread)
        if (sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            BlockPos center = blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
                if (random.nextFloat() < 0.45F && sl.getBlockState(p).isAir()
                        && sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) {
                    sl.setBlock(p.immutable(), Blocks.FIRE.defaultBlockState(), 11);
                }
            }
        }
        discard();
    }

}
