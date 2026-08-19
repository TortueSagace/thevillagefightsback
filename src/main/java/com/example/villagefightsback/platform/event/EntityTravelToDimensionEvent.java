package com.example.villagefightsback.platform.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * An entity is about to change dimension. The Limbo containment leash hangs off this, so it has to keep firing
 * even though the teleport itself is done through Fabric's dimension API now.
 */
public class EntityTravelToDimensionEvent extends EntityEvent {
    private final ResourceKey<Level> dimension;

    public EntityTravelToDimensionEvent(Entity entity, ResourceKey<Level> dimension) {
        super(entity);
        this.dimension = dimension;
    }

    public ResourceKey<Level> getDimension() {
        return this.dimension;
    }
}
