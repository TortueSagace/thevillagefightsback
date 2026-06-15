package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;

/** The Limbo Ravager: a 2x, 3x-HP, ghostly beast (see {@link LimboRaider}). At 2x a ravager is enormous. */
public class LimboRavagerEntity extends Ravager implements LimboRaider {
    public LimboRavagerEntity(EntityType<? extends Ravager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)       // 3x a ravager's 100
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75)
                .add(Attributes.ATTACK_DAMAGE, 12.0)     // 3x applied via LimboRaidEvents#onHurt
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }
}
