package com.example.villagergunners.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client: open the Village Gravemind upgrade screen, carrying the clicked village's current
 * level and emerald progress (the client has no access to the server-side {@code GravemindData}).
 */
public class OpenGravemindScreenPacket {
    public final int level;
    public final int maxLevel;
    public final int emeralds;
    public final int costNext; // -1 if already at max level
    public final int villagers; // defenders within the gravemind's radius, by type
    public final int guards;
    public final int golems;

    public OpenGravemindScreenPacket(int level, int maxLevel, int emeralds, int costNext,
                                     int villagers, int guards, int golems) {
        this.level = level;
        this.maxLevel = maxLevel;
        this.emeralds = emeralds;
        this.costNext = costNext;
        this.villagers = villagers;
        this.guards = guards;
        this.golems = golems;
    }

    public static void encode(OpenGravemindScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.level);
        buf.writeVarInt(msg.maxLevel);
        buf.writeVarInt(msg.emeralds);
        buf.writeVarInt(msg.costNext);
        buf.writeVarInt(msg.villagers);
        buf.writeVarInt(msg.guards);
        buf.writeVarInt(msg.golems);
    }

    public static OpenGravemindScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenGravemindScreenPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(OpenGravemindScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagergunners.client.GravemindScreenHooks.open(msg)));
        ctx.get().setPacketHandled(true);
    }
}
