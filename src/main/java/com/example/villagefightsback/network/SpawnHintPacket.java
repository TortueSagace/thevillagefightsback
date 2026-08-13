package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -&gt; client: show the starter-bell hint at the bottom of the screen for {@code ticks}.
 *
 * <p>Carries only the duration, like {@link VillageDiscoveryPacket}: the line itself is a constant on the client
 * so nothing has to travel over the wire.
 *
 * <p>1.21 note: main's Forge encode/decode pair plus a {@code DistExecutor} hop is replaced by the payload's
 * STREAM_CODEC and a plain client-class call. The dist guard is not lost -- the registrar only ever hands this
 * payload to a client handler, so the handler body is by construction client-only, exactly as the other
 * server-to-client payloads on this branch already are.
 */
public record SpawnHintPacket(int ticks) implements CustomPacketPayload {
    public static final Type<SpawnHintPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("spawn_hint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnHintPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpawnHintPacket::ticks,
                    SpawnHintPacket::new);

    @Override
    public Type<SpawnHintPacket> type() {
        return TYPE;
    }

    public static void handle(SpawnHintPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.VillageDiscoveryHud.showHint(msg.ticks()));
    }
}
