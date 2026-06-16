package com.example.villagefightsback;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Emerald Virus Arrow item: fires an {@link EmeraldVirusArrowEntity}. Crafted from 8 arrows around a
 * Potion of Emerald Virus (no expensive lingering potion required). The {@code amplifier} sets the virus
 * strength the fired arrow inflicts on hit: 0 = Virus I (the base arrow), 1 = Virus II (the level-II arrow).
 */
public class EmeraldVirusArrowItem extends ArrowItem {
    private final int amplifier;

    public EmeraldVirusArrowItem(Properties properties, int amplifier) {
        super(properties);
        this.amplifier = amplifier;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter) {
        EmeraldVirusArrowEntity arrow = new EmeraldVirusArrowEntity(level, shooter);
        arrow.setVirusAmplifier(amplifier);
        return arrow;
    }
}
