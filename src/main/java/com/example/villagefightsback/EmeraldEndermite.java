package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Endermite: an endermite recruited to the emerald faction. Hostile combat unit that keeps its
 * own vanilla attack; emerald goals are layered on top via {@link EmeraldMobs#addEmeraldGoals(net.minecraft.world.entity.Mob, boolean)}.
 */
public class EmeraldEndermite extends Endermite implements EmeraldUnit {
    public EmeraldEndermite(EntityType<? extends Endermite> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        EmeraldMobs.addEmeraldGoals(this, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Endermite.createAttributes();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
