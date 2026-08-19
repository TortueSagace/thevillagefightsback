package com.example.villagefightsback.network;

import java.util.function.Supplier;

import com.example.villagefightsback.LimboAbility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.example.villagefightsback.platform.network.NetworkContext;

/** Client -> server: the player pressed the Limbo-banish key. The server gates it (full set + cooldown). */
public class LimboBeamPacket {
    public LimboBeamPacket() {}

    public static void encode(LimboBeamPacket msg, FriendlyByteBuf buf) {}

    public static LimboBeamPacket decode(FriendlyByteBuf buf) {
        return new LimboBeamPacket();
    }

    public static void handle(LimboBeamPacket msg, Supplier<NetworkContext> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) LimboAbility.tryFire(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
