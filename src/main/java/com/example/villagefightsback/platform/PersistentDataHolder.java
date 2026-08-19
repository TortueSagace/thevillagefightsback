package com.example.villagefightsback.platform;

import net.minecraft.nbt.CompoundTag;

/**
 * A scratch tag that rides along with an entity and is saved with it.
 *
 * <p>Forge gives every entity one of these ({@code Entity.getPersistentData()}) and the mod leans on it hard --
 * 56 call sites stamp roles, stages and flags onto villagers, guards and golems. Vanilla has nothing like it, so
 * a mixin adds the field to Entity and this interface is how the rest of the mod reaches it (through
 * {@link PersistentData#of}).
 */
public interface PersistentDataHolder {
    CompoundTag getPersistentData();
}
