package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

/** A chunk came or went. Server-side only here, which is where the mod uses it. */
public class ChunkEvent extends Event {
    private final ChunkAccess chunk;
    private final LevelAccessor level;

    public ChunkEvent(ChunkAccess chunk, LevelAccessor level) {
        this.chunk = chunk;
        this.level = level;
    }

    public ChunkAccess getChunk() {
        return this.chunk;
    }

    public LevelAccessor getLevel() {
        return this.level;
    }

    public static class Load extends ChunkEvent {
        public Load(ChunkAccess chunk, LevelAccessor level) {
            super(chunk, level);
        }
    }

    public static class Unload extends ChunkEvent {
        public Unload(ChunkAccess chunk, LevelAccessor level) {
            super(chunk, level);
        }
    }
}
