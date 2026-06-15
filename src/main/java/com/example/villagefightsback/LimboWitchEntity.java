package com.example.villagefightsback;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Limbo Witch: a 2x, 3x-HP, ghostly witch (see {@link LimboRaider}). Alongside her vanilla harmful potions
 * she sometimes lobs a Volatile Limbo Potion, banishing whoever it bursts on to the Limbo. Drops Forgotten
 * Powder and a Volatile Limbo Potion (its loot table). The 3x-damage rule applies to her potion damage too.
 */
public class LimboWitchEntity extends Witch implements LimboRaider {
    private static final float VOLATILE_CHANCE = 0.30F; // chance a given throw is a Volatile Limbo Potion

    public LimboWitchEntity(EntityType<? extends Witch> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0)      // reach village targets from the raid spawn distance
                .add(Attributes.MAX_HEALTH, 78.0);       // 3x a witch's 26
    }

    /** Sometimes throw a Volatile Limbo Potion (same arc/throw as a vanilla witch potion) instead of a normal
     *  one. Never thrown at fellow raiders (it would banish an ally). */
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (!isDrinkingPotion() && !(target instanceof Raider) && getRandom().nextFloat() < VOLATILE_CHANCE) {
            Vec3 v = target.getDeltaMovement();
            double dx = target.getX() + v.x - getX();
            double dy = target.getEyeY() - 1.1 - getY();
            double dz = target.getZ() + v.z - getZ();
            double dh = Math.sqrt(dx * dx + dz * dz);
            VolatileLimboPotionEntity pot = new VolatileLimboPotionEntity(level(), this);
            pot.setXRot(pot.getXRot() - 20.0F);
            pot.shoot(dx, dy + dh * 0.2, dz, 0.75F, 8.0F);
            if (!isSilent()) {
                level().playSound((Player) null, getX(), getY(), getZ(), SoundEvents.WITCH_THROW,
                        getSoundSource(), 1.0F, 0.8F + getRandom().nextFloat() * 0.4F);
            }
            level().addFreshEntity(pot);
        } else {
            super.performRangedAttack(target, velocity);
        }
    }
}
