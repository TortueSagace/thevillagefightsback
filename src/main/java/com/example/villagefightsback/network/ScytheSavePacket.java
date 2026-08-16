package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -&gt; client: play the totem-of-undying save animation, but holding the Limbo Scythe.
 *
 * <p>Carries nothing. The whole message is "it happened to you"; the scythe is a constant on the client, so
 * there is no reason to put an item on the wire.
 *
 * <p>A packet rather than {@code broadcastEntityEvent(entity, 35)} because that vanilla event hard-codes the
 * totem: it emits totem particles and calls {@code displayItemActivation} with a totem stack, and the item
 * shown is exactly the part being replaced here. The screen animation is client-only anyway
 * ({@code GameRenderer#displayItemActivation}), so it has to be asked for rather than broadcast.
 *
 * <p>1.21 note: main's Forge encode/decode pair plus a {@code DistExecutor} hop becomes a payload record with
 * a {@link StreamCodec#unit} codec -- nothing to read or write -- and a plain client-class call, the same
 * shape {@link LimboBeamPacket} and {@link OpenModeSelectPacket} already use on this branch for main's other
 * payload-free packets. The dist guard is not lost: the registrar only ever hands this payload to a client
 * handler, so the handler body is by construction client-only.
 */
public record ScytheSavePacket() implements CustomPacketPayload {
    public static final Type<ScytheSavePacket> TYPE =
            new Type<>(TheVillageFightsBack.id("scythe_save"));

    // No payload fields; a unit codec that reads/writes nothing and returns a fresh instance.
    public static final StreamCodec<RegistryFriendlyByteBuf, ScytheSavePacket> STREAM_CODEC =
            StreamCodec.unit(new ScytheSavePacket());

    @Override
    public Type<ScytheSavePacket> type() {
        return TYPE;
    }

    public static void handle(ScytheSavePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> com.example.villagefightsback.client.ScytheSaveFx.play());
    }
}
