package com.example.villagefightsback;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A Limbo armour piece: an {@link ArmorItem} that always shows the enchantment shimmer, so the gear itself
 *  looks shiny on the icon, in hand, and worn (on top of the animated green-glint texture). Every piece also
 *  carries a two-line description of its strength and the full-set special attack. */
public class LimboArmorItem extends ArmorItem {
    public LimboArmorItem(ArmorMaterial material, EquipmentSlot slot, Properties properties) {
        super(material, slot, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // WORN armour animation: the icon/held textures animate via their .mcmeta sprite, but vanilla armour-layer
    // textures are static. So we cycle a series of pre-rendered frame textures (a flowing green shimmer baked
    // from the base layer) by game time, giving the worn set the same living look. Frames live at
    // textures/models/armor/limbo_layer_{1,2}_{0..ANIM_FRAMES-1}.png.
    private static final int ANIM_FRAMES = 16;
    private static final int ANIM_FRAME_TICKS = 3; // ticks per frame (~2.4s full loop)

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        if (type != null) return null;                       // no "overlay" layer for our gear
        int layer = (slot == EquipmentSlot.LEGS) ? 2 : 1;    // leggings use layer 2, everything else layer 1
        long time = (entity != null) ? entity.level.getGameTime() : 0L;
        int frame = (int) ((time / ANIM_FRAME_TICKS) % ANIM_FRAMES);
        return TheVillageFightsBack.MODID + ":textures/models/armor/limbo_layer_" + layer + "_" + frame + ".png";
    }

    // Two dedicated tip keys (NOT ".desc") so ModTooltips does not also append a line (it only adds ".desc").
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.thevillagefightsback.limbo_armor.tip1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.thevillagefightsback.limbo_armor.tip2").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tip, flag);
    }
}
