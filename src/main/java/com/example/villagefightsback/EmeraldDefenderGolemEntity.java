package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Defender Golem: the emerald-faction version of the {@link DefenderGolemEntity}. It keeps the
 * defender golem body + 150 HP + the head-turret revolver (its inherited {@code aiStep} fires the bullet at
 * its current target), but belongs to the EMERALD faction so it hunts the village (shared emerald target
 * goals). Its head stays a Wall Defender (the head layer is inherited from the renderer) and it gets NO head
 * spike; only its BODY is reskinned with the emerald iron-golem texture.
 */
public class EmeraldDefenderGolemEntity extends DefenderGolemEntity implements EmeraldUnit {
    public EmeraldDefenderGolemEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();                 // vanilla golem movement + melee + look
        EmeraldMobs.addEmeraldGoals(this, false); // retarget the village; the inherited aiStep turret fires at it
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
