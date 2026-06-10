package com.example.villagergunners.network;

import com.example.villagergunners.VillagerGunners;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's single network channel. Registered from the mod constructor (common side). */
public final class ModNetwork {
    private ModNetwork() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VillagerGunners.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenGravemindScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenGravemindScreenPacket::encode)
                .decoder(OpenGravemindScreenPacket::decode)
                .consumerMainThread(OpenGravemindScreenPacket::handle)
                .add();
    }

    /** Send a packet to one player (server -> client). */
    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
