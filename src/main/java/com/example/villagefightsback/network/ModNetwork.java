package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's single network channel. Registered from the mod constructor (common side). */
public final class ModNetwork {
    private ModNetwork() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TheVillageFightsBack.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenBrainNodeScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenBrainNodeScreenPacket::encode)
                .decoder(OpenBrainNodeScreenPacket::decode)
                .consumerMainThread(OpenBrainNodeScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(EmeraldTintPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EmeraldTintPacket::encode)
                .decoder(EmeraldTintPacket::decode)
                .consumerMainThread(EmeraldTintPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScreenShakePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScreenShakePacket::encode)
                .decoder(ScreenShakePacket::decode)
                .consumerMainThread(ScreenShakePacket::handle)
                .add();
        CHANNEL.messageBuilder(LimboBeamPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LimboBeamPacket::encode)
                .decoder(LimboBeamPacket::decode)
                .consumerMainThread(LimboBeamPacket::handle)
                .add();
        CHANNEL.messageBuilder(LimboCooldownPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LimboCooldownPacket::encode)
                .decoder(LimboCooldownPacket::decode)
                .consumerMainThread(LimboCooldownPacket::handle)
                .add();
        CHANNEL.messageBuilder(VillageDiscoveryPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VillageDiscoveryPacket::encode)
                .decoder(VillageDiscoveryPacket::decode)
                .consumerMainThread(VillageDiscoveryPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpawnHintPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpawnHintPacket::encode)
                .decoder(SpawnHintPacket::decode)
                .consumerMainThread(SpawnHintPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.example.villagefightsback.ObliterationFurnaceScreenPacket.class, id++,
                        NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.example.villagefightsback.ObliterationFurnaceScreenPacket::encode)
                .decoder(com.example.villagefightsback.ObliterationFurnaceScreenPacket::decode)
                .consumerMainThread(com.example.villagefightsback.ObliterationFurnaceScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScytheSavePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScytheSavePacket::encode)
                .decoder(ScytheSavePacket::decode)
                .consumerMainThread(ScytheSavePacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenModeSelectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenModeSelectPacket::encode)
                .decoder(OpenModeSelectPacket::decode)
                .consumerMainThread(OpenModeSelectPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChooseDifficultyPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChooseDifficultyPacket::encode)
                .decoder(ChooseDifficultyPacket::decode)
                .consumerMainThread(ChooseDifficultyPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChooseExperiencePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChooseExperiencePacket::encode)
                .decoder(ChooseExperiencePacket::decode)
                .consumerMainThread(ChooseExperiencePacket::handle)
                .add();

        CHANNEL.messageBuilder(ChooseModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChooseModePacket::encode)
                .decoder(ChooseModePacket::decode)
                .consumerMainThread(ChooseModePacket::handle)
                .add();
        // Appended, never inserted: `id` is positional, so slotting a message into the middle silently
        // renumbers every one after it and a mismatched client reads the wrong packet as the right one.
        CHANNEL.messageBuilder(OpenReputationBookPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenReputationBookPacket::encode)
                .decoder(OpenReputationBookPacket::decode)
                .consumerMainThread(OpenReputationBookPacket::handle)
                .add();
        CHANNEL.messageBuilder(ReversedGravityPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReversedGravityPacket::encode)
                .decoder(ReversedGravityPacket::decode)
                .consumerMainThread(ReversedGravityPacket::handle)
                .add();
    }

    /** Send a packet to one player (server -> client). */
    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** Send a packet to every client tracking {@code entity} (server -> clients), including the entity itself. */
    public static void sendToTracking(Entity entity, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), msg);
    }

    /** Send a packet to every player currently in {@code dim} (server -> clients). */
    public static void sendToDimension(ResourceKey<Level> dim, Object msg) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dim), msg);
    }
}
