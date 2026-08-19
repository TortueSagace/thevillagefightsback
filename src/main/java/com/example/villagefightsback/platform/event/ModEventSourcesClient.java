package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.EventBus;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

/**
 * The client half of the firing sites: the shim events that Fabric already has a client callback for.
 *
 * <p>The camera, fog and per-entity render events are fired from client mixins instead, since there is no
 * callback for them.
 */
@Environment(EnvType.CLIENT)
public final class ModEventSourcesClient {
    private ModEventSourcesClient() {}

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(client ->
                EventBus.post(new TickEvent.ClientTickEvent(TickEvent.Phase.START)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            EventBus.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            // Forge fires a key event per keystroke; the one handler that listens just drains its key binding
            // with consumeClick(), so once a tick is both sufficient and the Fabric idiom.
            EventBus.post(new InputEvent.Key());
        });

        ItemTooltipCallback.EVENT.register((stack, context, lines) ->
                EventBus.post(new ItemTooltipEvent(stack, net.minecraft.client.Minecraft.getInstance().player,
                        lines, context)));
    }
}
