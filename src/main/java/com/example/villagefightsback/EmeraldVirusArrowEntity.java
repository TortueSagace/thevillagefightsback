package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Virus arrow: a normal arrow that also infects whatever living thing it hits with the Emerald
 * Virus (like a tipped arrow). Crafted cheaply from 8 arrows + a Potion of Emerald Virus.
 */
public class EmeraldVirusArrowEntity extends AbstractArrow {
    public EmeraldVirusArrowEntity(EntityType<? extends EmeraldVirusArrowEntity> type, Level level) {
        super(type, level);
    }

    public EmeraldVirusArrowEntity(Level level, LivingEntity shooter) {
        super(ModEntities.EMERALD_VIRUS_ARROW.get(), shooter, level);
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(ModItems.EMERALD_VIRUS_ARROW.get());
    }

    /** Called by AbstractArrow after the hit's damage is applied -- where tipped arrows add their effects. */
    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.addEffect(new MobEffectInstance(ModEffects.EMERALD_VIRUS.get(), EmeraldVirus.DURATION_TICKS),
                this.getEffectSource());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
