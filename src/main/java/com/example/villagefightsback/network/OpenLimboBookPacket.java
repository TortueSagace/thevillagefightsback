package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: open the Limbo Book.
 *
 * <p>Carries nothing. Unlike {@link OpenReputationBookPacket}, whose pages depend on a difficulty the client
 * never receives, every word of this book is static, so the payload exists purely to cross the side boundary.
 *
 * <p>1.21 note: main opens the screen straight from the item through a {@code DistExecutor} hop, which does not
 * exist here. A zero-field payload is the same trick this port already uses everywhere else that main reaches
 * for DistExecutor, and it keeps the client class off the dedicated server's classloader.
 */
public record OpenLimboBookPacket() implements CustomPacketPayload {
    public static final OpenLimboBookPacket INSTANCE = new OpenLimboBookPacket();

    public static final Type<OpenLimboBookPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("open_limbo_book"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLimboBookPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenLimboBookPacket> type() {
        return TYPE;
    }

    public static void handle(OpenLimboBookPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> com.example.villagefightsback.client.LimboBookScreen.show());
    }
}
