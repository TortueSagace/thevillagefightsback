package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client: this world was just created and nobody has picked a game mode yet, so open the mode screen.
 * Carries no payload; every option the screen offers is a fixed preset both sides already know.
 */
public class OpenModeSelectPacket {

    public OpenModeSelectPacket() {}

    public static void encode(OpenModeSelectPacket msg, FriendlyByteBuf buf) {}

    public static OpenModeSelectPacket decode(FriendlyByteBuf buf) {
        return new OpenModeSelectPacket();
    }

    public static void handle(OpenModeSelectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.ModeSelectHooks.open()));
        ctx.get().setPacketHandled(true);
    }
}
