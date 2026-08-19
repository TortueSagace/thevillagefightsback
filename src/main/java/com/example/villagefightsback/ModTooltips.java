package com.example.villagefightsback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.event.ItemTooltipEvent;
import com.example.villagefightsback.platform.SubscribeEvent;
import com.example.villagefightsback.platform.ModRegistries;

/**
 * Adds a short gray description line to the inventory tooltip of every block/egg in the mod -- driven purely
 * by lang: if a {@code <item-description-id>.desc} key exists, it is appended. Fertile/rich blocks are
 * skipped (by request), and items that already render their own description ({@link TooltipBlockItem},
 * {@link LimboPickaxeItem}) are left alone so the line never doubles up.
 */
public final class ModTooltips {
    private ModTooltips() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        if (item instanceof TooltipBlockItem || item instanceof LimboPickaxeItem) return; // already self-describing
        ResourceLocation id = ModRegistries.ITEMS.getKey(item);
        if (id == null || !id.getNamespace().equals(TheVillageFightsBack.MODID)) return;
        if (id.getPath().startsWith("fertile_") || id.getPath().startsWith("rich_")) return; // skip fertile/rich blocks
        String key = item.getDescriptionId() + ".desc";
        if (I18n.exists(key)) {
            event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
