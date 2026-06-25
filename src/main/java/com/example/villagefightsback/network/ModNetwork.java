package com.example.villagefightsback.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The mod's single network channel. Registered from the mod constructor (common side). */
public final class ModNetwork {
    private ModNetwork() {}

    private static final String PROTOCOL = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToClient(OpenBrainNodeScreenPacket.TYPE, OpenBrainNodeScreenPacket.STREAM_CODEC, OpenBrainNodeScreenPacket::handle);
        registrar.playToClient(EmeraldTintPacket.TYPE, EmeraldTintPacket.STREAM_CODEC, EmeraldTintPacket::handle);
        registrar.playToClient(ScreenShakePacket.TYPE, ScreenShakePacket.STREAM_CODEC, ScreenShakePacket::handle);
        registrar.playToServer(LimboBeamPacket.TYPE, LimboBeamPacket.STREAM_CODEC, LimboBeamPacket::handle);
        registrar.playToClient(LimboCooldownPacket.TYPE, LimboCooldownPacket.STREAM_CODEC, LimboCooldownPacket::handle);
        registrar.playToClient(VillageDiscoveryPacket.TYPE, VillageDiscoveryPacket.STREAM_CODEC, VillageDiscoveryPacket::handle);
    }

    /** Send a packet to one player (server -> client). */
    public static void sendTo(ServerPlayer player, CustomPacketPayload msg) {
        PacketDistributor.sendToPlayer(player, msg);
    }

    /** Send a packet to every client tracking {@code entity} (server -> clients), including the entity itself. */
    public static void sendToTracking(Entity entity, CustomPacketPayload msg) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, msg);
    }
}
