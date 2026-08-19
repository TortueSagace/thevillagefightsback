package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.server.MinecraftServer;

/** The server has finished starting and the worlds are loaded. */
public class ServerStartedEvent extends Event {
    private final MinecraftServer server;

    public ServerStartedEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return this.server;
    }
}
