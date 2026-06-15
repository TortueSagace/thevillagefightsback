package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Limbo Evoker: a 3x, 3x-HP, ghostly spellcaster (see {@link LimboRaider}). It keeps the vanilla evoker
 * spells (fangs + summon), but its summoned vexes are swapped for Limbo Vexes by {@link LimboRaidEvents}.
 * Drops 3 Totems of Undying (its loot table).
 */
public class LimboEvokerEntity extends Evoker implements LimboRaider {
    public LimboEvokerEntity(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)      // reach village targets from the raid spawn distance
                .add(Attributes.MAX_HEALTH, 72.0);       // 3x an evoker's 24
    }
}
