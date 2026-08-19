package com.example.villagefightsback.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Reaching an entity's persistent tag. See {@link PersistentDataHolder} for what it is and why it exists.
 *
 * <p>On Forge the call is {@code entity.getPersistentData()}. The method cannot be added to Entity at compile
 * time here, only at runtime through a mixin, so every call site goes through this instead.
 */
public final class PersistentData {
    private PersistentData() {}

    /**
     * The sub-tag that survives death. Forge's own name for it, kept so a player's flags carry over when a world
     * moves between the Forge build and this one -- the whole tag is stored under the same key as well.
     */
    public static final String PERSISTED_NBT_TAG = "PlayerPersisted";

    /** The entity's persistent tag, created on first use. Never null. */
    public static CompoundTag of(Entity entity) {
        return ((PersistentDataHolder) entity).getPersistentData();
    }

    /**
     * Carries the death-surviving sub-tag from the old player entity to the new one, which is what Forge does on
     * respawn. Without it a player's starter-bell and Limbo-book flags would reset every time they died.
     */
    public static void copyPersistedAcrossRespawn(Entity oldPlayer, Entity newPlayer) {
        CompoundTag persisted = of(oldPlayer).getCompound(PERSISTED_NBT_TAG);
        if (!persisted.isEmpty()) {
            of(newPlayer).put(PERSISTED_NBT_TAG, persisted.copy());
        }
    }
}
