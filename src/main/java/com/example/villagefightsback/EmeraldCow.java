package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Cow: a passive member of the emerald faction. It behaves like a vanilla cow but runs the shared
 * emerald-unit AI goals ({@link EmeraldMobs#addEmeraldGoals}) in passive mode and carries a small attack
 * damage so it can defend itself if those goals turn aggressive.
 */
public class EmeraldCow extends Cow implements EmeraldUnit {
    public EmeraldCow(EntityType<? extends Cow> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return true;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        EmeraldMobs.addEmeraldGoals(this, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Cow.createAttributes().add(Attributes.ATTACK_DAMAGE, 5.0D);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
