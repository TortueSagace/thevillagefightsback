package com.example.villagefightsback.platform.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** The experience a dying entity is about to drop. Cancelling drops none at all. */
public class LivingExperienceDropEvent extends LivingEvent {
    private final Player attackingPlayer;
    private final int originalExperience;
    private int droppedExperience;

    public LivingExperienceDropEvent(LivingEntity entity, Player attackingPlayer, int droppedExperience) {
        super(entity);
        this.attackingPlayer = attackingPlayer;
        this.originalExperience = droppedExperience;
        this.droppedExperience = droppedExperience;
    }

    public Player getAttackingPlayer() {
        return this.attackingPlayer;
    }

    public int getOriginalExperience() {
        return this.originalExperience;
    }

    public int getDroppedExperience() {
        return this.droppedExperience;
    }

    public void setDroppedExperience(int droppedExperience) {
        this.droppedExperience = droppedExperience;
    }
}
