package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: shake the camera for {@code durationTicks} at {@code intensity} (degrees of jitter). */
public record ScreenShakePacket(float intensity, int durationTicks) implements CustomPacketPayload {
    public static final Type<ScreenShakePacket> TYPE =
            new Type<>(TheVillageFightsBack.id("screen_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenShakePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, ScreenShakePacket::intensity,
                    ByteBufCodecs.VAR_INT, ScreenShakePacket::durationTicks,
                    ScreenShakePacket::new);

    @Override
    public Type<ScreenShakePacket> type() {
        return TYPE;
    }

    public static void handle(ScreenShakePacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.ScreenShakeClient.add(msg.intensity(), msg.durationTicks()));
    }
}
