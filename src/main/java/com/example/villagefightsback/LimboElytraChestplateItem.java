package com.example.villagefightsback;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Limbo chestplate that ALSO works as an elytra: wearing it is equivalent to wearing both a Limbo
 * chestplate and an elytra at once. It is still a {@link LimboArmorItem} (same Limbo material, foil
 * shimmer, fire resistance, the two limbo tip lines) and counts as the chest piece of the Limbo set
 * (see {@link LimboGear}). Gliding is enabled through the NeoForge elytra hooks below; the vanilla
 * ElytraLayer draws the wings automatically whenever {@code canElytraFly} is true.
 */
public class LimboElytraChestplateItem extends LimboArmorItem {
    public LimboElytraChestplateItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.CHESTPLATE, properties);
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!entity.level().isClientSide()) {
            int next = flightTicks + 1;
            if (next % 20 == 0) {
                stack.hurtAndBreak(1, entity, EquipmentSlot.CHEST);
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tip, TooltipFlag flag) {
        // Keep the two limbo tip lines, then add the flight line.
        super.appendHoverText(stack, context, tip, flag);
        tip.add(Component.translatable("item.thevillagefightsback.limbo_elytra_chestplate.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}
