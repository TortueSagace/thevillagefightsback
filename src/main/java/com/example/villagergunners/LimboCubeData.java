package com.example.villagergunners;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers which cube-origin chunks of the Limbo have already had their floating bedrock cube built, so a
 * cube is generated exactly once (and player edits to it are never overwritten on reload). Stored on the
 * Limbo level's own data storage. Only cube-origin chunks are recorded, so the set stays small.
 */
public class LimboCubeData extends SavedData {
    private static final String NAME = "gobacktoyourworld_limbo_cubes";

    private final Set<Long> built = new HashSet<>();

    public LimboCubeData() {}

    public static LimboCubeData get(ServerLevel limbo) {
        return limbo.getDataStorage().computeIfAbsent(LimboCubeData::load, LimboCubeData::new, NAME);
    }

    /** Mark a cube-origin chunk built; returns true if this is the first time (i.e. build it now). */
    public boolean markBuilt(long chunkKey) {
        if (built.add(chunkKey)) { setDirty(); return true; }
        return false;
    }

    public static LimboCubeData load(CompoundTag tag) {
        LimboCubeData d = new LimboCubeData();
        for (long k : tag.getLongArray("built")) d.built.add(k);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("built", built.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
