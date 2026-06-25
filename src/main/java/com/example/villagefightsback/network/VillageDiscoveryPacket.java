package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: show the village-discovery message at the bottom of the screen for {@code ticks}. */
public record VillageDiscoveryPacket(int ticks) implements CustomPacketPayload {
    public static final Type<VillageDiscoveryPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("village_discovery"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageDiscoveryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, VillageDiscoveryPacket::ticks,
                    VillageDiscoveryPacket::new);

    @Override
    public Type<VillageDiscoveryPacket> type() {
        return TYPE;
    }

    public static void handle(VillageDiscoveryPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.VillageDiscoveryHud.show(msg.ticks()));
    }
}
