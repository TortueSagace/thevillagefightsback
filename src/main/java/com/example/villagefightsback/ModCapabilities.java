package com.example.villagefightsback;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capabilities, which in 1.21 are registered rather than overridden.
 *
 * <p>On 1.20.1 a block entity answered {@code getCapability} itself. In 1.21 that method is gone: a
 * capability is registered against the block entity TYPE on the mod bus, and the lookup is a function from
 * (block entity, side) to the handler. Hence this class, which has no 1.20.1 counterpart.
 *
 * <p>One entry: the Obliteration Furnace's fuel letterbox, so a hopper can feed it. The side is ignored --
 * emeralds may be pushed in from any face, since the block has no orientation-dependent inventory to speak
 * of and refusing a face would only be a puzzle with no answer.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {
    private ModCapabilities() {}

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlocks.OBLITERATION_FURNACE_BE.get(),
                (be, side) -> be.fuelHandler());
    }
}
