package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;

/**
 * The "Brain Node Warden": mechanically a vanilla {@link Warden} (all of its AI, sonic boom, anger, digging
 * and stats inherited untouched), but green-themed and rendered with the Village Brain Node block in place
 * of its head -- see {@code BrainNodeWardenRenderer}. Summoned via its spawn egg.
 */
public class BrainNodeWardenEntity extends Warden {
    public BrainNodeWardenEntity(EntityType<? extends Warden> type, Level level) {
        super(type, level);
    }
}
