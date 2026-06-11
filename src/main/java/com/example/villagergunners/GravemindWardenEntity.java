package com.example.villagergunners;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;

/**
 * The "Gravemind Warden": mechanically a vanilla {@link Warden} (all of its AI, sonic boom, anger, digging
 * and stats inherited untouched), but green-themed and rendered with the Village Gravemind block in place
 * of its head -- see {@code GravemindWardenRenderer}. Summoned via its spawn egg.
 */
public class GravemindWardenEntity extends Warden {
    public GravemindWardenEntity(EntityType<? extends Warden> type, Level level) {
        super(type, level);
    }
}
