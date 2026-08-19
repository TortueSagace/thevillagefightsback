package com.example.villagefightsback.platform.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The first moment of an attack, before invulnerability, shields or armour are considered. Cancelling it drops
 * the hit entirely, which is what the mod uses for its immunities.
 */
public class LivingAttackEvent extends LivingEvent {
    private final DamageSource source;
    private final float amount;

    public LivingAttackEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source;
        this.amount = amount;
    }

    public DamageSource getSource() {
        return this.source;
    }

    public float getAmount() {
        return this.amount;
    }
}
