package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;

/**
 * Keyboard input. Client-only.
 *
 * <p>Forge fires this per key action; here it is fired once per client tick instead, because the one handler that
 * listens just drains its key binding with consumeClick() -- which is the Fabric idiom anyway, and gives exactly
 * the same result.
 */
public class InputEvent extends Event {
    public static class Key extends InputEvent {
    }
}
