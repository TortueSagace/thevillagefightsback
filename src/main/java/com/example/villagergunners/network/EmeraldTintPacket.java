package com.example.villagergunners.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client: an entity's Emerald-Virus green-tint progress (0..1). The infection is server-side
 * state; clients need this to render the host greener over time. Sent to all players tracking the entity
 * (and on start-tracking, so a player who walks up to an already-green host still sees it).
 */
public class EmeraldTintPacket {
    public final int entityId;
    public final float progress;

    public EmeraldTintPacket(int entityId, float progress) {
        this.entityId = entityId;
        this.progress = progress;
    }

    public static void encode(EmeraldTintPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeFloat(msg.progress);
    }

    public static EmeraldTintPacket decode(FriendlyByteBuf buf) {
        return new EmeraldTintPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(EmeraldTintPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagergunners.client.EmeraldTintClient.put(msg.entityId, msg.progress)));
        ctx.get().setPacketHandled(true);
    }
}
