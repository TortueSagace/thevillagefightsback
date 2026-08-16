package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -&gt; client: show one of the bottom-of-screen hints for {@code ticks}.
 *
 * <p>Carries a duration and a LINE NUMBER, never the line. Like {@link VillageDiscoveryPacket}, the text is a
 * constant on the client, so nothing but an int travels; adding a hint means adding a string to
 * {@code VillageDiscoveryHud} and a name to the constants below, and the wire format never changes.
 *
 * <p>1.21 note: main's Forge encode/decode pair plus a {@code DistExecutor} hop is replaced by the payload's
 * STREAM_CODEC and a plain client-class call. The dist guard is not lost -- the registrar only ever hands this
 * payload to a client handler, so the handler body is by construction client-only, exactly as the other
 * server-to-client payloads on this branch already are.
 */
public record SpawnHintPacket(int ticks, int id) implements CustomPacketPayload {
    /** The starter bell, shown once to a player who spawned away from any village. */
    public static final int HINT_BELL = 0;
    /** A rich block was mined: the village's people are the ones who know how to get emeralds out of it. */
    public static final int HINT_RICH_VILLAGERS = 1;
    /** A rich block was mined: the rarer line, pointing at the Obliteration Furnace instead. */
    public static final int HINT_RICH_OBLITERATE = 2;

    public static final Type<SpawnHintPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("spawn_hint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnHintPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SpawnHintPacket::ticks,
                    ByteBufCodecs.VAR_INT, SpawnHintPacket::id,
                    SpawnHintPacket::new);

    /** The starter-bell hint, or 0 ticks to hide whatever is showing. */
    public SpawnHintPacket(int ticks) {
        this(ticks, HINT_BELL);
    }

    @Override
    public Type<SpawnHintPacket> type() {
        return TYPE;
    }

    public static void handle(SpawnHintPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.VillageDiscoveryHud.showHint(msg.ticks(), msg.id()));
    }
}
