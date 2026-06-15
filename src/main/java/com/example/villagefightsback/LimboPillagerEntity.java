package com.example.villagefightsback;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

/** The Limbo Pillager: a 3x, 3x-HP, ghostly crossbow illager (see {@link LimboRaider}). */
public class LimboPillagerEntity extends Pillager implements LimboRaider {
    public LimboPillagerEntity(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.MAX_HEALTH, 72.0)        // 3x a pillager's 24
                .add(Attributes.ATTACK_DAMAGE, 5.0)      // 3x applied via LimboRaidEvents#onHurt
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(LimboRaider.SCALE); // hitbox grows with the model (3x)
    }
}
