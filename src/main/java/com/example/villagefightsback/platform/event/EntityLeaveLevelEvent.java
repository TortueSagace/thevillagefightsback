package com.example.villagefightsback.platform.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** An entity is being removed from a level -- unloaded, killed, or moved to another dimension. */
public class EntityLeaveLevelEvent extends EntityEvent {
    private final Level level;

    public EntityLeaveLevelEvent(Entity entity, Level level) {
        super(entity);
        this.level = level;
    }

    public Level getLevel() {
        return this.level;
    }
}
