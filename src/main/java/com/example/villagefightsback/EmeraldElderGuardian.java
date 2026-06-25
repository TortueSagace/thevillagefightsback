package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.Level;

/**
 * The Emerald Elder Guardian: an EMERALD-faction reskin of the vanilla Elder Guardian. Combat unit, so it keeps
 * its native attack and is not {@link #emeraldPassive() passive}; its targeting is replaced with the shared
 * emerald-faction rules via {@link EmeraldMobs#addEmeraldGoals}. MAX_HEALTH is set to 80.
 */
public class EmeraldElderGuardian extends ElderGuardian implements EmeraldUnit {
    public EmeraldElderGuardian(EntityType<? extends ElderGuardian> type, Level level) {
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
        // The builder is backed by a HashMap, so re-adding MAX_HEALTH simply overwrites Guardian's value.
        return Guardian.createAttributes().add(Attributes.MAX_HEALTH, 80.0D);
    }
}
