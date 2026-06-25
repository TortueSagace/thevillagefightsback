package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Zombie: an emerald-faction zombie that fights for the village. It keeps vanilla zombie loot and
 * behaviour, but is wired with the shared emerald combat goals (see {@link EmeraldMobs#addEmeraldGoals}).
 */
public class EmeraldZombie extends Zombie implements EmeraldUnit {
    public EmeraldZombie(EntityType<? extends Zombie> type, Level level) {
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

    /** Emerald undead don't burn in daylight. */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    /** Never convert to a vanilla Drowned underwater -- an emerald zombie stays an emerald-faction unit (a vanilla
     *  Drowned would have the wrong loot/AI and no longer count as emerald). */
    @Override
    protected boolean convertsInWater() {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes();
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
