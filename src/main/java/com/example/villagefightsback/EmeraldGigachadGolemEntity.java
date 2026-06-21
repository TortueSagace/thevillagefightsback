package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Gigachad Golem: the emerald-faction version of the {@link GigachadGolemEntity}. It inherits the
 * full gigachad kit (rocket launcher, iron-set armour, 200 HP, {@link com.example.villagefightsback.goal.GolemRocketGoal})
 * via the inherited {@code finalizeSpawn -> VillageEvents.armGolemFull}, but belongs to the EMERALD faction and
 * so hunts the village instead of defending it (shared emerald target goals). Its rockets detonate GREEN
 * (see {@link RocketEntity#onHit}). Reskinned with the emerald-gold shell + a green head spike.
 */
public class EmeraldGigachadGolemEntity extends GigachadGolemEntity implements EmeraldUnit {
    public EmeraldGigachadGolemEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();                 // vanilla golem movement + melee + look
        EmeraldMobs.addEmeraldGoals(this, false); // retarget the village (clears the golem's defend-village goals)
    }

    // finalizeSpawn is inherited from GigachadGolemEntity -> armGolemFull (rocket launcher, armour, 200 HP,
    // GolemRocketGoal). The rocket goal is faction-aware and fires at whatever the emerald goals target.

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
