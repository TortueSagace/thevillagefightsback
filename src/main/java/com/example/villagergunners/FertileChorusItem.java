package com.example.villagergunners;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fertile Chorus: a food item that behaves exactly like vanilla chorus fruit (it feeds you and teleports you
 * on eat -- see {@link ChorusFruitItem}). On top of that, there is a small chance (1%) that eating it gives
 * the host the {@link EmeraldDiarrheaEffect} "Emerald Diarrhea". Dropped by the Fertile Chorus Plant.
 */
public class FertileChorusItem extends ChorusFruitItem {
    public FertileChorusItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // super keeps the chorus-fruit behaviour: applies the food and teleports the eater.
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player && level.random.nextFloat() < 0.01F) {
            entity.addEffect(new MobEffectInstance(ModEffects.EMERALD_DIARRHEA.get(), 600)); // 30s
        }
        return result;
    }
}
