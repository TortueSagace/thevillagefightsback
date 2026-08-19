package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.server.MinecraftServer;

/** The server is shutting down but the worlds are still there. Ten handlers use it to drop their state. */
public class ServerStoppingEvent extends Event {
    private final MinecraftServer server;

    public ServerStoppingEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return this.server;
    }
}
