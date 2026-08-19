package com.example.villagefightsback.platform.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * An entity is being added to a level. Cancelling keeps it out.
 *
 * <p>Forge fires this on both sides; the Fabric callback behind it is server-only, so anything that depended on
 * the client firing has to be handled elsewhere. {@link #getLevel} is still worth checking.
 */
public class EntityJoinLevelEvent extends EntityEvent {
    private final Level level;
    private final boolean loadedFromDisk;

    public EntityJoinLevelEvent(Entity entity, Level level, boolean loadedFromDisk) {
        super(entity);
        this.level = level;
        this.loadedFromDisk = loadedFromDisk;
    }

    public Level getLevel() {
        return this.level;
    }

    /** True when the entity came back from the save file rather than being spawned fresh. */
    public boolean loadedFromDisk() {
        return this.loadedFromDisk;
    }
}
