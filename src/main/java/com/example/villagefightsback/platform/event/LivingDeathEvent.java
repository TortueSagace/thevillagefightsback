package com.example.villagefightsback.platform.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A living entity is about to die. Cancelling it keeps the entity alive with its health restored to 1. */
public class LivingDeathEvent extends LivingEvent {
    private final DamageSource source;

    public LivingDeathEvent(LivingEntity entity, DamageSource source) {
        super(entity);
        this.source = source;
    }

    public DamageSource getSource() {
        return this.source;
    }
}
