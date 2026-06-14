package com.example.villagefightsback;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Emerald Virus Arrow item: fires an {@link EmeraldVirusArrowEntity}. Crafted from 8 arrows around a
 * Potion of Emerald Virus (no expensive lingering potion required).
 */
public class EmeraldVirusArrowItem extends ArrowItem {
    public EmeraldVirusArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter) {
        return new EmeraldVirusArrowEntity(level, shooter);
    }
}
