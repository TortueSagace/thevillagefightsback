package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;

/** The Limbo Vex: a 3x, 3x-HP, ghostly vex summoned by the Limbo Evoker (see {@link LimboRaider}). */
public class LimboVexEntity extends Vex implements LimboRaider {
    public LimboVexEntity(EntityType<? extends Vex> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 42.0)        // 3x a vex's 14
                .add(Attributes.ATTACK_DAMAGE, 4.0);     // 3x applied via LimboRaidEvents#onHurt
    }
}
