package com.example.villagefightsback.platform;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Stand-in for Forge's {@code ServerLifecycleHooks.getCurrentServer()}: the running server, or null when there is
 * none (a client sitting at the main menu). Seventeen call sites reach for it.
 */
public final class ServerRef {
    private ServerRef() {}

    private static volatile MinecraftServer current;

    /** Wired from the mod initializer, before anything can ask. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> current = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = null);
    }

    /** The running server, or null. Callers already null-check: on a client this is null between worlds. */
    public static MinecraftServer getCurrentServer() {
        return current;
    }
}
