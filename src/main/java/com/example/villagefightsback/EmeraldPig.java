package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;

/**
 * The Emerald Pig: a pig converted into the EMERALD faction (v1.0.3 "mob infection"). A normally-passive
 * species, so it gains the shared emerald melee attack and hunts every non-spared target (see
 * {@link EmeraldMobs#addEmeraldGoals}).
 */
public class EmeraldPig extends Pig implements EmeraldUnit {
    public EmeraldPig(EntityType<? extends Pig> type, Level level) {
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
        return Pig.createAttributes().add(Attributes.ATTACK_DAMAGE, 5.0D);
    }

}
