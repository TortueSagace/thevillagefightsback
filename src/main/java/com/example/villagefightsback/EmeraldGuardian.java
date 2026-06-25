package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.Level;

/**
 * The Emerald Guardian: an emerald-faction member of the village's defence. A vanilla Guardian that fights
 * with its laser beam ({@link EmeraldUnit} combatant, not passive) and shares the emerald-faction goals via
 * {@link EmeraldMobs#addEmeraldGoals}.
 */
public class EmeraldGuardian extends Guardian implements EmeraldUnit {
    public EmeraldGuardian(EntityType<? extends Guardian> type, Level level) {
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
        return Guardian.createAttributes();
    }
}
