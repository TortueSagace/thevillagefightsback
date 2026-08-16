package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -&gt; client: open the Reputation Book.
 *
 * <p>Carries the difficulty and the reader's current standing, and not one word of the book. The text is a
 * client-side constant, like {@link SpawnHintPacket}'s lines -- but the DIFFICULTY has to travel, because it
 * lives in a COMMON {@code ForgeConfigSpec} and Forge does not sync those to the client. Without it a
 * single-player client would read the right book by luck and a multiplayer client would read Neutral's.
 *
 * <p>1.21 note: main's encode/decode pair plus a {@code DistExecutor} hop becomes a payload record with a
 * STREAM_CODEC and a plain client-class call, exactly as {@link SpawnHintPacket} on this branch already does.
 * The dist guard is not lost -- the registrar only ever hands this payload to a client handler, so the
 * handler body is by construction client-only. The wire shape is unchanged: a var-int difficulty and a
 * FIXED-WIDTH signed int for the standing ({@code ByteBufCodecs.INT}, not {@code VAR_INT}), because standing
 * goes below zero and a var-int would spend five bytes saying so.
 */
public record OpenReputationBookPacket(int difficulty, int reputation) implements CustomPacketPayload {
    public static final Type<OpenReputationBookPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("open_reputation_book"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenReputationBookPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenReputationBookPacket::difficulty,
                    ByteBufCodecs.INT, OpenReputationBookPacket::reputation,   // signed: standing goes below zero
                    OpenReputationBookPacket::new);

    @Override
    public Type<OpenReputationBookPacket> type() {
        return TYPE;
    }

    public static void handle(OpenReputationBookPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> com.example.villagefightsback.client.ReputationBookScreen.show(
                msg.difficulty(), msg.reputation()));
    }
}
