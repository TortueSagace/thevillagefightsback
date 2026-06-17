package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: show the village-discovery message at the bottom of the screen for {@code ticks}. */
public class VillageDiscoveryPacket {
    public final int ticks;

    public VillageDiscoveryPacket(int ticks) {
        this.ticks = ticks;
    }

    public static void encode(VillageDiscoveryPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ticks);
    }

    public static VillageDiscoveryPacket decode(FriendlyByteBuf buf) {
        return new VillageDiscoveryPacket(buf.readVarInt());
    }

    public static void handle(VillageDiscoveryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.VillageDiscoveryHud.show(msg.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
