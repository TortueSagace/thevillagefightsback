package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/** Mob spawning. Only the finalize step, which is where the mod inspects and vetoes new mobs. */
public class MobSpawnEvent extends Event {
    private final Mob mob;
    private final ServerLevelAccessor level;
    private final MobSpawnType spawnType;

    public MobSpawnEvent(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
        this.mob = mob;
        this.level = level;
        this.spawnType = spawnType;
    }

    public Mob getEntity() {
        return this.mob;
    }

    public ServerLevelAccessor getLevel() {
        return this.level;
    }

    public MobSpawnType getSpawnType() {
        return this.spawnType;
    }

    /**
     * A mob has been built and placed but has not yet had finalizeSpawn run on it. {@link #setSpawnCancelled}
     * keeps it out of the world.
     */
    public static class FinalizeSpawn extends MobSpawnEvent {
        private boolean spawnCancelled;

        public FinalizeSpawn(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
            super(mob, level, spawnType);
        }

        public void setSpawnCancelled(boolean cancelled) {
            this.spawnCancelled = cancelled;
        }

        public boolean isSpawnCancelled() {
            return this.spawnCancelled;
        }
    }
}
