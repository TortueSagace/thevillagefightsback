package com.example.villagefightsback.platform.network;

import java.util.concurrent.Executor;

import net.minecraft.server.level.ServerPlayer;

/**
 * Stand-in for Forge's {@code NetworkEvent.Context}, so every packet's {@code handle(msg, ctx)} reads the same on
 * this branch as on the Forge one.
 *
 * <p>Fabric hands packet callbacks to the network thread and expects the handler to hop to the game thread itself.
 * That is what {@link #enqueueWork} does here, so the Forge habit of wrapping the body in it is not just
 * decoration: touching the world straight from the callback would be a race.
 */
public final class NetworkContext {
    private final Executor mainThread;
    private final ServerPlayer sender;

    public NetworkContext(Executor mainThread, ServerPlayer sender) {
        this.mainThread = mainThread;
        this.sender = sender;
    }

    /** Runs {@code work} on the game thread. */
    public void enqueueWork(Runnable work) {
        this.mainThread.execute(work);
    }

    /** The player who sent this packet, or null for a server-to-client packet. */
    public ServerPlayer getSender() {
        return this.sender;
    }

    /**
     * No-op. Forge uses this to assert a packet was consumed; Fabric has no such bookkeeping, and the call sites
     * are kept only so the packet classes stay identical to the Forge branch.
     */
    public void setPacketHandled(boolean handled) {
        // no-op -- see the javadoc
    }
}
