package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;

/**
 * The Emerald Enderman: an emerald-faction enderman that joins the village's fight. A hostile combat unit
 * (not passive) wired up with the shared emerald goals via {@link EmeraldMobs#addEmeraldGoals(Mob, boolean)}.
 */
public class EmeraldEnderman extends EnderMan implements EmeraldUnit {
    public EmeraldEnderman(EntityType<? extends EnderMan> type, Level level) {
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
        return EnderMan.createAttributes();
    }
}
