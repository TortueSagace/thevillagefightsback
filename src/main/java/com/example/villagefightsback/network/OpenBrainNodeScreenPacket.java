package com.example.villagefightsback.network;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client: open the Village Brain Node upgrade screen, carrying the clicked village's current
 * level and emerald progress (the client has no access to the server-side {@code BrainNodeData}).
 */
public class OpenBrainNodeScreenPacket {
    public final int level;
    public final int maxLevel;
    public final int emeralds;
    public final int costNext; // -1 if already at max level
    public final int villagers; // defenders within the brain node's radius, by type
    public final int guards;
    public final int golems;
    public final int fertilized; // count of fertilized (fertile/rich) blocks in this dimension
    public final List<String> reflexLog; // the brain's last (up to 10) reflex log lines, oldest first

    public OpenBrainNodeScreenPacket(int level, int maxLevel, int emeralds, int costNext,
                                     int villagers, int guards, int golems, int fertilized, List<String> reflexLog) {
        this.level = level;
        this.maxLevel = maxLevel;
        this.emeralds = emeralds;
        this.costNext = costNext;
        this.villagers = villagers;
        this.guards = guards;
        this.golems = golems;
        this.fertilized = fertilized;
        this.reflexLog = reflexLog;
    }

    public static void encode(OpenBrainNodeScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.level);
        buf.writeVarInt(msg.maxLevel);
        buf.writeVarInt(msg.emeralds);
        buf.writeVarInt(msg.costNext);
        buf.writeVarInt(msg.villagers);
        buf.writeVarInt(msg.guards);
        buf.writeVarInt(msg.golems);
        buf.writeVarInt(msg.fertilized);
        buf.writeCollection(msg.reflexLog, FriendlyByteBuf::writeUtf);
    }

    public static OpenBrainNodeScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenBrainNodeScreenPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readList(FriendlyByteBuf::readUtf));
    }

    public static void handle(OpenBrainNodeScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.BrainNodeScreenHooks.open(msg)));
        ctx.get().setPacketHandled(true);
    }
}
