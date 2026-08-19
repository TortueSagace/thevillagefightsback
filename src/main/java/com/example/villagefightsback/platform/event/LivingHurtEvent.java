package com.example.villagefightsback.platform.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Damage that has passed the invulnerability and shield checks and is about to be applied. The amount can still be
 * changed, which is why this cannot be Fabric's ALLOW_DAMAGE callback (that one only votes yes or no).
 */
public class LivingHurtEvent extends LivingEvent {
    private final DamageSource source;
    private float amount;

    public LivingHurtEvent(LivingEntity entity, DamageSource source, float amount) {
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

    public void setAmount(float amount) {
        this.amount = amount;
    }
}
