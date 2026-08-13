package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: this world was just created and nobody has picked a game mode yet, so open the mode screen.
 * Carries no payload; every option the screen offers is a fixed preset both sides already know.
 *
 * <p>1.21 note: an empty payload uses {@link StreamCodec#unit}, which reads and writes nothing, the same shape
 * {@link LimboBeamPacket} already uses on this branch for main's other payload-free packet.
 */
public record OpenModeSelectPacket() implements CustomPacketPayload {
    public static final Type<OpenModeSelectPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("open_mode_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenModeSelectPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenModeSelectPacket());

    @Override
    public Type<OpenModeSelectPacket> type() {
        return TYPE;
    }

    public static void handle(OpenModeSelectPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> com.example.villagefightsback.client.ModeSelectHooks.open());
    }
}
