package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Horse: a vanilla horse belonging to the emerald faction. It is passive (see
 * {@link EmeraldUnit#emeraldPassive()}) and gets the shared emerald goals wired in via
 * {@link EmeraldMobs#addEmeraldGoals(net.minecraft.world.entity.Mob, boolean)}.
 */
public class EmeraldHorse extends Horse implements EmeraldUnit {
    public EmeraldHorse(EntityType<? extends Horse> type, Level level) {
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
        return Horse.createBaseHorseAttributes().add(Attributes.ATTACK_DAMAGE, 11.0D);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
