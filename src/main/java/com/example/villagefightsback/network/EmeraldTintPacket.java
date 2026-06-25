package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: an entity's Emerald-Virus green-tint progress (0..1). The infection is server-side
 * state; clients need this to render the host greener over time. Sent to all players tracking the entity
 * (and on start-tracking, so a player who walks up to an already-green host still sees it).
 */
public record EmeraldTintPacket(int entityId, float progress) implements CustomPacketPayload {
    public static final Type<EmeraldTintPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("emerald_tint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmeraldTintPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EmeraldTintPacket::entityId,
                    ByteBufCodecs.FLOAT, EmeraldTintPacket::progress,
                    EmeraldTintPacket::new);

    @Override
    public Type<EmeraldTintPacket> type() {
        return TYPE;
    }

    public static void handle(EmeraldTintPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.EmeraldTintClient.put(msg.entityId(), msg.progress()));
    }
}
