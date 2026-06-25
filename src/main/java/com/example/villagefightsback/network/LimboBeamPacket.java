package com.example.villagefightsback.network;

import com.example.villagefightsback.LimboAbility;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the player pressed the Limbo-banish key. The server gates it (full set + cooldown). */
public record LimboBeamPacket() implements CustomPacketPayload {
    public static final Type<LimboBeamPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("limbo_beam"));

    // No payload fields; a unit codec that reads/writes nothing and returns a fresh instance.
    public static final StreamCodec<RegistryFriendlyByteBuf, LimboBeamPacket> STREAM_CODEC =
            StreamCodec.unit(new LimboBeamPacket());

    @Override
    public Type<LimboBeamPacket> type() {
        return TYPE;
    }

    public static void handle(LimboBeamPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) LimboAbility.tryFire(player);
        });
    }
}
