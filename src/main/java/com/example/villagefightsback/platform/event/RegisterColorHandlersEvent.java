package com.example.villagefightsback.platform.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.level.ItemLike;

/** Block and item tint handlers, in Forge's shape, over Fabric's colour provider registry. */
@Environment(EnvType.CLIENT)
public final class RegisterColorHandlersEvent {
    private RegisterColorHandlersEvent() {}

    @Environment(EnvType.CLIENT)
    public static final class Item {
        public void register(ItemColor handler, ItemLike... items) {
            ColorProviderRegistry.ITEM.register(handler, items);
        }
    }

    @Environment(EnvType.CLIENT)
    public static final class Block {
        public void register(BlockColor handler, net.minecraft.world.level.block.Block... blocks) {
            ColorProviderRegistry.BLOCK.register(handler, blocks);
        }
    }
}
