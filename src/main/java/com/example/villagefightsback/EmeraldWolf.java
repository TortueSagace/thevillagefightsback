package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;

/**
 * The Emerald Wolf: a wolf belonging to the emerald faction. Hostile (not passive) and uses the shared
 * emerald combat goals on top of vanilla Wolf behaviour (which already includes ATTACK_DAMAGE and a melee
 * attack goal).
 */
public class EmeraldWolf extends Wolf implements EmeraldUnit {
    public EmeraldWolf(EntityType<? extends Wolf> type, Level level) {
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
        return Wolf.createAttributes();
    }
}
