package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.world.level.LevelAccessor;

/** Level-wide events. Only the one-off initial spawn choice, which the mod uses to seat the first village. */
public class LevelEvent extends Event {
    private final LevelAccessor level;

    public LevelEvent(LevelAccessor level) {
        this.level = level;
    }

    public LevelAccessor getLevel() {
        return this.level;
    }

    /**
     * Fires exactly once in a world's life, while its initial spawn point is being chosen. Cancelling it means
     * the mod has set the spawn itself and vanilla should not.
     */
    public static class CreateSpawnPosition extends LevelEvent {
        public CreateSpawnPosition(LevelAccessor level) {
            super(level);
        }
    }
}
