package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/** Explosions. Only the detonate step, where the affected block and entity lists can still be edited. */
public class ExplosionEvent extends Event {
    private final Level level;
    private final Explosion explosion;

    public ExplosionEvent(Level level, Explosion explosion) {
        this.level = level;
        this.explosion = explosion;
    }

    public Level getLevel() {
        return this.level;
    }

    public Explosion getExplosion() {
        return this.explosion;
    }

    /**
     * The blast has been calculated but nothing has been broken or hurt yet. Both lists are live -- removing a
     * position from {@link #getAffectedBlocks} spares that block.
     */
    public static class Detonate extends ExplosionEvent {
        private final List<BlockPos> affectedBlocks;
        private final List<Entity> affectedEntities;

        public Detonate(Level level, Explosion explosion, List<BlockPos> affectedBlocks,
                        List<Entity> affectedEntities) {
            super(level, explosion);
            this.affectedBlocks = affectedBlocks;
            this.affectedEntities = affectedEntities;
        }

        public List<BlockPos> getAffectedBlocks() {
            return this.affectedBlocks;
        }

        public List<Entity> getAffectedEntities() {
            return this.affectedEntities;
        }
    }
}
