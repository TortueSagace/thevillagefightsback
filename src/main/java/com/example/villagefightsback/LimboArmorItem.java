package com.example.villagefightsback;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A Limbo armour piece: an {@link ArmorItem} that always shows the enchantment shimmer, so the gear itself
 *  looks shiny on the icon, in hand, and worn (on top of the animated green-glint texture). Every piece also
 *  carries a two-line description of its strength and the full-set special attack. */
public class LimboArmorItem extends ArmorItem {
    public LimboArmorItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // Two dedicated tip keys (NOT ".desc") so ModTooltips does not also append a line (it only adds ".desc").
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.thevillagefightsback.limbo_armor.tip1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.thevillagefightsback.limbo_armor.tip2").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tip, flag);
    }
}
