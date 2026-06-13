package com.example.villagergunners;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Limbo Pickaxe: a netherite-grade pickaxe (smithed from a netherite pickaxe + a Limbo Ingot + the Limbo
 * Smithing Template) that can additionally mine the unmineable -- bedrock and reinforced deepslate. The actual
 * breaking of those -1-hardness blocks is handled in {@link LimboPickaxeEvents}; this class is the item
 * identity + the tooltip.
 */
public class LimboPickaxeItem extends PickaxeItem {
    public LimboPickaxeItem(Properties properties) {
        super(LimboToolTier.INSTANCE, 1, -2.8F, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // the gear itself looks shiny
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("item.gobacktoyourworld.limbo_pickaxe.desc").withStyle(ChatFormatting.DARK_GREEN));
    }
}
