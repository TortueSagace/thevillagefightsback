package com.example.villagergunners;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Limbo Arrow item: a black-tipped arrow that fires a {@link LimboArrowEntity}. Crafted from 8 arrows
 * around a Limbo Potion (which leaves a glass bottle).
 */
public class LimboArrowItem extends ArrowItem {
    public LimboArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter) {
        return new LimboArrowEntity(level, shooter);
    }
}
