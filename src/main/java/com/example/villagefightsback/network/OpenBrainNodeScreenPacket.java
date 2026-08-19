package com.example.villagefightsback.network;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.DistExecutor;
import com.example.villagefightsback.platform.network.NetworkContext;

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
    public final int organic; // banked organic mass (1.1.2 organic system; 0 / hidden when that mode is off)
    public final int reputation; // the OPENING player's standing with the village faction (global, not per node)
    public final List<String> reflexLog; // the brain's last (up to 10) reflex (emerald-spend) log lines, oldest first
    public final List<String> organicLog; // the brain's last (up to 10) organic-spawn decisions, oldest first
    public final List<String> reputationLog; // the last (up to 10) reputation changes, world-wide, oldest first

    public OpenBrainNodeScreenPacket(int level, int maxLevel, int emeralds, int costNext,
                                     int villagers, int guards, int golems, int fertilized, int organic,
                                     int reputation, List<String> reflexLog, List<String> organicLog,
                                     List<String> reputationLog) {
        this.level = level;
        this.maxLevel = maxLevel;
        this.emeralds = emeralds;
        this.costNext = costNext;
        this.villagers = villagers;
        this.guards = guards;
        this.golems = golems;
        this.fertilized = fertilized;
        this.organic = organic;
        this.reputation = reputation;
        this.reflexLog = reflexLog;
        this.organicLog = organicLog;
        this.reputationLog = reputationLog;
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
        buf.writeVarInt(msg.organic);
        buf.writeCollection(msg.reflexLog, FriendlyByteBuf::writeUtf);
        buf.writeCollection(msg.organicLog, FriendlyByteBuf::writeUtf);
        // Appended, not inserted: encode and decode are positional, so a field added in the middle would be
        // read back into the wrong one.
        buf.writeVarInt(msg.reputation);
        buf.writeCollection(msg.reputationLog, FriendlyByteBuf::writeUtf);
    }

    public static OpenBrainNodeScreenPacket decode(FriendlyByteBuf buf) {
        // Read in WIRE order, which is not constructor order: reputation travels last but is the tenth
        // argument, so the reads are named rather than inlined.
        int level = buf.readVarInt(), maxLevel = buf.readVarInt(), emeralds = buf.readVarInt();
        int costNext = buf.readVarInt(), villagers = buf.readVarInt(), guards = buf.readVarInt();
        int golems = buf.readVarInt(), fertilized = buf.readVarInt(), organic = buf.readVarInt();
        List<String> reflexLog = buf.readList(FriendlyByteBuf::readUtf);
        List<String> organicLog = buf.readList(FriendlyByteBuf::readUtf);
        int reputation = buf.readVarInt();
        List<String> reputationLog = buf.readList(FriendlyByteBuf::readUtf);
        return new OpenBrainNodeScreenPacket(level, maxLevel, emeralds, costNext, villagers, guards, golems,
                fertilized, organic, reputation, reflexLog, organicLog, reputationLog);
    }

    public static void handle(OpenBrainNodeScreenPacket msg, Supplier<NetworkContext> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.BrainNodeScreenHooks.open(msg)));
        ctx.get().setPacketHandled(true);
    }
}
