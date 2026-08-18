package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client: gravity in this dimension is reversed for {@code durationTicks}, so turn the world
 * upside down for that long.
 *
 * <p>Purely cosmetic -- it carries no motion. The rising itself is driven server-side by a hidden
 * levitation effect, which vanilla already syncs and which the client already predicts. There is also no
 * matching "stop" packet: the client counts its own ticks down and eases back out.
 */
public class ReversedGravityPacket {
    public final int durationTicks;

    public ReversedGravityPacket(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public static void encode(ReversedGravityPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.durationTicks);
    }

    public static ReversedGravityPacket decode(FriendlyByteBuf buf) {
        return new ReversedGravityPacket(buf.readVarInt());
    }

    public static void handle(ReversedGravityPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.ReversedGravityClient.begin(msg.durationTicks)));
        ctx.get().setPacketHandled(true);
    }
}
