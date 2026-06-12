package com.example.villagergunners;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Limbo Arrow projectile: on striking any living entity it banishes that entity to Limbo -- exactly like
 * the Unleashed Gravemind's beam ({@link LimboDimension#sendToLimbo}) -- and deals no damage. A Limbo-gravel
 * return marker is dropped automatically (by {@link LimboManager}, since the target changes dimension).
 */
public class LimboArrowEntity extends AbstractArrow {
    public LimboArrowEntity(EntityType<? extends LimboArrowEntity> type, Level level) {
        super(type, level);
    }

    public LimboArrowEntity(Level level, LivingEntity shooter) {
        super(ModEntities.LIMBO_ARROW.get(), shooter, level);
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(ModItems.LIMBO_ARROW.get());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide() && result.getEntity() instanceof LivingEntity target) {
            LimboDimension.sendToLimbo(target); // banish, no damage (like the beam)
        }
        discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
