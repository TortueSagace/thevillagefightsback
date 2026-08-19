package com.example.villagefightsback.platform.event;

import net.minecraft.world.entity.LivingEntity;

/**
 * A mob is about to take a new target. Cancelling it leaves the old target in place, which is how the mod keeps
 * the village's own units from turning on each other.
 */
public class LivingChangeTargetEvent extends LivingEvent {
    private final LivingEntity originalTarget;
    private LivingEntity newTarget;

    public LivingChangeTargetEvent(LivingEntity entity, LivingEntity newTarget) {
        super(entity);
        this.originalTarget = newTarget;
        this.newTarget = newTarget;
    }

    public LivingEntity getNewTarget() {
        return this.newTarget;
    }

    public void setNewTarget(LivingEntity newTarget) {
        this.newTarget = newTarget;
    }

    public LivingEntity getOriginalTarget() {
        return this.originalTarget;
    }
}
