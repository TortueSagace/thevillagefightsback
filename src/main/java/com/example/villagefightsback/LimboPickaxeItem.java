package com.example.villagefightsback;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;

/**
 * The Limbo Pickaxe: a netherite-grade pickaxe (smithed from a netherite pickaxe + a Limbo Ingot + the Limbo
 * Smithing Template) that can additionally mine the unmineable -- bedrock and reinforced deepslate. The actual
 * breaking of those -1-hardness blocks is handled in {@link LimboPickaxeEvents}; this class is the item
 * identity + the tooltip.
 */
public class LimboPickaxeItem extends PickaxeItem {
    public LimboPickaxeItem(Properties properties) {
        // 1.21: PickaxeItem's ctor no longer takes attack damage/speed; supply them via Properties.attributes.
        super(LimboToolTier.INSTANCE, properties.attributes(
                DiggerItem.createAttributes(LimboToolTier.INSTANCE, 1.0F, -2.8F)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // the gear itself looks shiny
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tip, flag);
        tip.add(Component.translatable("item.thevillagefightsback.limbo_pickaxe.desc").withStyle(ChatFormatting.DARK_GREEN));
    }
}
