package com.example.villagefightsback;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Obliterator: the Limbo Sword taken past what a sword should be.
 *
 * <p>On its own it is simply the harder weapon -- {@value #DISPLAY_DAMAGE} attack damage against the Limbo
 * Sword's 9, which is what the recipe's dragon egg buys. That is its whole behaviour until an
 * {@code Obliteration Furnace} is burning somewhere in the world; from then on every hit also marks the victim
 * with Obliteration, and a marked creature that dies is struck off the world's spawn tables for as long as that
 * furnace keeps burning.
 *
 * <p>The damage number is a MODIFIER, not the total. A SwordItem's tooltip shows
 * {@code tier bonus + modifier + 1}, and {@link LimboToolTier} contributes 4, so the modifier below is nine and
 * the weapon reads as fourteen. Getting this wrong is invisible until you hover the item, which is why the sum
 * is written down here rather than left for the next person to rediscover.
 */
public class ObliteratorItem extends SwordItem {
    /** What the tooltip shows. See the note above: the constructor takes the modifier, not this. */
    public static final int DISPLAY_DAMAGE = 14;
    private static final int TIER_BONUS = 4;      // LimboToolTier
    private static final int VANILLA_BASE = 1;
    public static final int DAMAGE_MODIFIER = DISPLAY_DAMAGE - TIER_BONUS - VANILLA_BASE;

    public ObliteratorItem(Properties properties) {
        super(LimboToolTier.INSTANCE, DAMAGE_MODIFIER, -2.4F, properties);
    }

    /** Always enchanted-looking, like the rest of the Limbo set. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("An evolved Limbo Sword.").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("With an Obliteration Furnace burning, its hits mark prey for erasure.")
                .withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, level, lines, flag);
    }

    /**
     * Mark the victim when a furnace is burning.
     *
     * <p>Deliberately does NOT check whether the furnace is close by: the Obliterator works anywhere as long as
     * one is lit somewhere, and it is the FURNACE that later decides which of several claims the kill (the
     * nearest one). Splitting it that way keeps the weapon's rule short enough to state in a tooltip.
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        ObliterationEvents.onObliteratorHit(target, attacker);
        return result;
    }
}
