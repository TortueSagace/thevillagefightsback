package com.example.villagefightsback;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Emerald Diarrhea": the rare, funny side-effect of eating Fertile Chorus ({@link FertileChorusItem}). It does
 * nothing for its whole duration and then, at the very last tick, fires once: it drains some hunger, makes the
 * player noisily expel an emerald, and grants the {@code emerald_diarrhea} advancement.
 */
public class EmeraldDiarrheaEffect extends MobEffect {
    public EmeraldDiarrheaEffect() {
        super(MobEffectCategory.HARMFUL, 0x3FA34D);
    }

    /** Fire exactly once, on the final tick of the effect. */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration == 1;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player)) return true;

        // Drain hunger.
        int foodLevel = player.getFoodData().getFoodLevel();
        player.getFoodData().setFoodLevel(Math.max(0, foodLevel - 6));

        // Expel an emerald at the player.
        player.drop(new ItemStack(Items.EMERALD), true);

        // A funny, undignified sound.
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LLAMA_SPIT, SoundSource.PLAYERS, 1.0F, 0.6F);

        // Award the achievement.
        MinecraftServer server = player.server;
        AdvancementHolder advancement = server.getAdvancements()
                .get(TheVillageFightsBack.id("emerald_diarrhea"));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "got_it");
        }
        return true;
    }
}
