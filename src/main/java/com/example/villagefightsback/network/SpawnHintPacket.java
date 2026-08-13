package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -&gt; client: show the starter-bell hint at the bottom of the screen for {@code ticks}.
 *
 * <p>Carries only the duration, like {@link VillageDiscoveryPacket}: the line itself is a constant on the client
 * so nothing has to travel over the wire.
 */
public class SpawnHintPacket {
    public final int ticks;

    public SpawnHintPacket(int ticks) {
        this.ticks = ticks;
    }

    public static void encode(SpawnHintPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ticks);
    }

    public static SpawnHintPacket decode(FriendlyByteBuf buf) {
        return new SpawnHintPacket(buf.readVarInt());
    }

    public static void handle(SpawnHintPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.VillageDiscoveryHud.showHint(msg.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
