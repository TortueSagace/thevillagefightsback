package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: gravity in this dimension is reversed for {@code durationTicks}, so turn the world
 * upside down for that long.
 *
 * <p>Purely cosmetic -- it carries no motion. The rising itself is driven server-side by a hidden
 * levitation effect, which vanilla already syncs and which the client already predicts. There is also no
 * matching "stop" packet: the client counts its own ticks down and eases back out.
 */
public record ReversedGravityPacket(int durationTicks) implements CustomPacketPayload {
    public static final Type<ReversedGravityPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("reversed_gravity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReversedGravityPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ReversedGravityPacket::durationTicks,
                    ReversedGravityPacket::new);

    @Override
    public Type<ReversedGravityPacket> type() {
        return TYPE;
    }

    public static void handle(ReversedGravityPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.ReversedGravityClient.begin(msg.durationTicks()));
    }
}
