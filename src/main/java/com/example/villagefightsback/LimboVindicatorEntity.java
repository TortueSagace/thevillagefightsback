package com.example.villagefightsback;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

/** The Limbo Vindicator: a 3x, 3x-HP, ghostly axe illager (see {@link LimboRaider}). */
public class LimboVindicatorEntity extends Vindicator implements LimboRaider {
    public LimboVindicatorEntity(EntityType<? extends Vindicator> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 12.0)
                .add(Attributes.MAX_HEALTH, 72.0)        // 3x a vindicator's 24
                .add(Attributes.ATTACK_DAMAGE, 5.0);     // 3x applied via LimboRaidEvents#onHurt
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(LimboRaider.SCALE);
    }
}
