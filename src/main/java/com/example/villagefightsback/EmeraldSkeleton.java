package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;

/**
 * The Emerald Skeleton: a skeleton recruited into the EMERALD faction. Keeps its vanilla ranged-bow combat,
 * but its targeting is replaced with the emerald-faction rules via {@link EmeraldMobs#addEmeraldGoals}.
 */
public class EmeraldSkeleton extends Skeleton implements EmeraldUnit {
    public EmeraldSkeleton(EntityType<? extends Skeleton> type, Level level) {
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

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes();
    }

}
