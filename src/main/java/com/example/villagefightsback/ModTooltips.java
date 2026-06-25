package com.example.villagefightsback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Adds a short gray description line to the inventory tooltip of every block/egg in the mod -- driven purely
 * by lang: if a {@code <item-description-id>.desc} key exists, it is appended. Fertile/rich blocks are
 * skipped (by request), and items that already render their own description ({@link TooltipBlockItem},
 * {@link LimboPickaxeItem}) are left alone so the line never doubles up.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ModTooltips {
    private ModTooltips() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        if (item instanceof TooltipBlockItem || item instanceof LimboPickaxeItem) return; // already self-describing
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !id.getNamespace().equals(TheVillageFightsBack.MODID)) return;
        if (id.getPath().startsWith("fertile_") || id.getPath().startsWith("rich_")) return; // skip fertile/rich blocks
        String key = item.getDescriptionId() + ".desc";
        if (I18n.exists(key)) {
            event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
