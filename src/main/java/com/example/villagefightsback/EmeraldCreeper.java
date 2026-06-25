package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

/**
 * The Emerald Creeper: a creeper in the EMERALD faction (v1.0.3 "mob infection"). It keeps its native combat
 * behaviour (it explodes), but its targeting is replaced by the shared emerald-faction rules wired in via
 * {@link EmeraldMobs}, so it hunts every non-spared target instead of only players.
 */
public class EmeraldCreeper extends Creeper implements EmeraldUnit {
    public EmeraldCreeper(EntityType<? extends Creeper> type, Level level) {
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
        return Creeper.createAttributes();
    }
}
