package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;

/**
 * The Emerald Cat: a vanilla cat reskinned as an emerald-faction unit. It is passive (emeraldPassive() ==
 * true) and gains the shared emerald AI goals; Cat extends TamableAnimal, so the EntityType is bounded by Cat.
 */
public class EmeraldCat extends Cat implements EmeraldUnit {
    public EmeraldCat(EntityType<? extends Cat> type, Level level) {
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
        EmeraldMobs.addExplorer(this); // brain-summoned scout: roam to seek out infection sources
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Large follow range so it detects infectable mobs from afar and goes hunting them.
        return Cat.createAttributes().add(Attributes.ATTACK_DAMAGE, 5.0D).add(Attributes.FOLLOW_RANGE, 48.0D);
    }

}
