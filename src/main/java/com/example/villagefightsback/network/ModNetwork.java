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
        // 1.3.x additions. main registers these through Forge's SimpleChannel messageBuilder with an explicit
        // numeric id and a direction; the 1.21 registrar takes the direction from the method it is registered
        // with (playToClient / playToServer) and derives the id from the payload TYPE, so the ordering below
        // just mirrors main's for readability -- it carries no wire meaning any more.
        registrar.playToClient(SpawnHintPacket.TYPE, SpawnHintPacket.STREAM_CODEC, SpawnHintPacket::handle);
        registrar.playToClient(OpenModeSelectPacket.TYPE, OpenModeSelectPacket.STREAM_CODEC, OpenModeSelectPacket::handle);
        registrar.playToServer(ChooseModePacket.TYPE, ChooseModePacket.STREAM_CODEC, ChooseModePacket::handle);
        // 1.4.0 / 1.5.0 additions.
        registrar.playToServer(ChooseDifficultyPacket.TYPE, ChooseDifficultyPacket.STREAM_CODEC, ChooseDifficultyPacket::handle);
        registrar.playToClient(ScytheSavePacket.TYPE, ScytheSavePacket.STREAM_CODEC, ScytheSavePacket::handle);
        registrar.playToClient(OpenReputationBookPacket.TYPE, OpenReputationBookPacket.STREAM_CODEC, OpenReputationBookPacket::handle);
        // The Obliteration Furnace screen snapshot. Its payload class deliberately lives beside the block in
        // the root package rather than here (see its javadoc), hence the qualified names.
        registrar.playToClient(com.example.villagefightsback.ObliterationFurnaceScreenPacket.TYPE,
                com.example.villagefightsback.ObliterationFurnaceScreenPacket.STREAM_CODEC,
                com.example.villagefightsback.ObliterationFurnaceScreenPacket::handle);
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
