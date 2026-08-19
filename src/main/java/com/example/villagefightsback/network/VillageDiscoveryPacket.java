package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.DistExecutor;
import com.example.villagefightsback.platform.network.NetworkContext;

/**
 * Server -&gt; client: show the village-discovery message at the bottom of the screen for {@code ticks}.
 *
 * <p>{@code warn} carries whether the second line -- the one promising consequences -- applies. It travels on
 * the wire rather than being decided on the client, because the difficulty is server state and the client has
 * no business guessing at it. On Peaceful there are no consequences to threaten, so the threat is dropped.
 */
public class VillageDiscoveryPacket {
    public final int ticks;
    public final boolean warn;

    public VillageDiscoveryPacket(int ticks, boolean warn) {
        this.ticks = ticks;
        this.warn = warn;
    }

    public static void encode(VillageDiscoveryPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ticks);
        buf.writeBoolean(msg.warn);
    }

    public static VillageDiscoveryPacket decode(FriendlyByteBuf buf) {
        return new VillageDiscoveryPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(VillageDiscoveryPacket msg, Supplier<NetworkContext> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.VillageDiscoveryHud.show(msg.ticks, msg.warn)));
        ctx.get().setPacketHandled(true);
    }
}
