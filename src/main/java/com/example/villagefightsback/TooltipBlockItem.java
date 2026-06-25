package com.example.villagefightsback;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A BlockItem that shows a fixed, translatable gray description line in its tooltip. */
public class TooltipBlockItem extends BlockItem {
    private final String descKey;

    public TooltipBlockItem(Block block, Properties props, String descKey) {
        super(block, props);
        this.descKey = descKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
