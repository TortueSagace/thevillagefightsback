package com.example.villagefightsback;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.Level;

/**
 * The Emerald Chicken: a chicken reskinned as a passive emerald-faction unit. It keeps vanilla chicken
 * behavior, but gains the shared passive emerald goals (see {@link EmeraldMobs#addEmeraldGoals}) and lays
 * EMERALD eggs instead of normal ones.
 */
public class EmeraldChicken extends Chicken implements EmeraldUnit {
    public EmeraldChicken(EntityType<? extends Chicken> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return true;
    }

    /** Lay an EMERALD egg instead of a normal one. We pre-empt vanilla's egg drop: when the timer is about to
     *  fire we lay our emerald egg and reset {@code eggTime} BEFORE super runs, so Chicken.aiStep's own
     *  {@code --eggTime} never reaches 0 (and thus never drops a vanilla egg). */
    @Override
    public void aiStep() {
        if (!this.level().isClientSide() && this.isAlive() && !this.isBaby() && !this.isChickenJockey()
                && this.eggTime <= 1) {
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F,
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.spawnAtLocation(ModItems.EMERALD_EGG.get());
            this.eggTime = this.random.nextInt(6000) + 6000;
        }
        super.aiStep();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        EmeraldMobs.addEmeraldGoals(this, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Chicken.createAttributes().add(Attributes.ATTACK_DAMAGE, 2.0D);
    }
}
