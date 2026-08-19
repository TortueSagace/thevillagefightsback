package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * The tick events. Forge fires each twice, once at the start of the tick and once at the end, and the mod checks
 * {@link #phase} everywhere, so both are fired here too.
 */
public class TickEvent extends Event {
    public enum Phase {
        START,
        END
    }

    public final Phase phase;

    public TickEvent(Phase phase) {
        this.phase = phase;
    }

    /** Once per server tick. By far the mod's busiest event: 28 handlers hang off it. */
    public static class ServerTickEvent extends TickEvent {
        private final MinecraftServer server;

        public ServerTickEvent(Phase phase, MinecraftServer server) {
            super(phase);
            this.server = server;
        }

        public MinecraftServer getServer() {
            return this.server;
        }
    }

    /** Once per client tick. */
    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) {
            super(phase);
        }
    }

    /** Once per player per tick. */
    public static class PlayerTickEvent extends TickEvent {
        public final Player player;

        public PlayerTickEvent(Phase phase, Player player) {
            super(phase);
            this.player = player;
        }
    }
}
