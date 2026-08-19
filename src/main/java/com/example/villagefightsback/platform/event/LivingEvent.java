package com.example.villagefightsback.platform.event;

import net.minecraft.world.entity.LivingEntity;

/** Base for events about a living entity, plus the per-tick event Forge nests inside it. */
public class LivingEvent extends EntityEvent {
    public LivingEvent(LivingEntity entity) {
        super(entity);
    }

    @Override
    public LivingEntity getEntity() {
        return (LivingEntity) super.getEntity();
    }

    /** Fired once per living entity per tick, from a LivingEntity.tick mixin. */
    public static class LivingTickEvent extends LivingEvent {
        public LivingTickEvent(LivingEntity entity) {
            super(entity);
        }
    }
}
