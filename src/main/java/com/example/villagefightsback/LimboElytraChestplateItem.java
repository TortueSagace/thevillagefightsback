package com.example.villagefightsback;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A Limbo chestplate that ALSO works as an elytra: wearing it is equivalent to wearing both a Limbo
 * chestplate and an elytra at once. It is still a {@link LimboArmorItem} (same Limbo material, foil
 * shimmer, fire resistance, the two limbo tip lines) and counts as the chest piece of the Limbo set
 * (see {@link LimboGear}).
 *
 * <p>Forge asks the item whether it can glide. Fabric asks a global event instead, so the answer lives in
 * {@link LimboElytraEvents}, wired up from the mod initializer -- including the durability cost, which vanilla
 * only applies to a real elytra.
 */
public class LimboElytraChestplateItem extends LimboArmorItem {
    public LimboElytraChestplateItem(ArmorMaterial material, Properties properties) {
        super(material, Type.CHESTPLATE, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        // Keep the two limbo tip lines, then add the flight line.
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("item.thevillagefightsback.limbo_elytra_chestplate.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}
