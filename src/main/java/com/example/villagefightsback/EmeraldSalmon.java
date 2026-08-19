package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.level.Level;

/**
 * The Emerald Salmon: a salmon converted to the EMERALD faction. A normally-passive species, so via
 * {@link EmeraldMobs#addEmeraldGoals} it gains a melee attack and hunts every non-spared target. Salmon
 * (AbstractFish) has no createAttributes(), so attributes are built from {@link Mob#createMobAttributes()}.
 */
public class EmeraldSalmon extends Salmon implements EmeraldUnit {
    public EmeraldSalmon(EntityType<? extends Salmon> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return true;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        EmeraldMobs.addEmeraldGoals(this, true);
        EmeraldMobs.addAquaticExplorer(this); // water scout: sweep large water zones outward, hunting for prey
    }

    /** The emerald salmon never suffocates out of water -- it hunts on dry land like the rest of the faction. */
    @Override
    protected void handleAirSupply(int airSupply) {
        this.setAirSupply(300);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.7D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D); // a wide net so the water scout spots prey across the water
    }

}
