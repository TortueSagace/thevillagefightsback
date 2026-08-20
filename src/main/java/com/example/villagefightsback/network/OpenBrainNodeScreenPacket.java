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
    public final int organic; // banked organic mass (1.1.2 organic system; 0 / hidden when that mode is off)
    public final int reputation; // the OPENING player's standing with the village faction (global, not per node)
    public final List<String> reflexLog; // the brain's last (up to 10) reflex (emerald-spend) log lines, oldest first
    public final List<String> organicLog; // the brain's last (up to 10) organic-spawn decisions, oldest first
    public final List<String> reputationLog; // the last (up to 10) reputation changes, world-wide, oldest first

    /**
     * SURVIVAL only: the metal tribute line shown under the emerald row, ready to draw. Empty in a Creative
     * world, which is also how the screen knows to keep its old height.
     *
     * <p>Preformatted on the server on purpose. The numbers and the colours are already decided there (see
     * BrainNodeData.Econ.copperFor and friends), and sending one string beats threading six counters through
     * two different packet formats and duplicating the rule client-side where it could drift.
     */
    public final String metalLine;

    /**
     * SURVIVAL only: replaces the emerald progress line when the emeralds are already in and only metal is
     * missing. Empty otherwise.
     *
     * <p>"12 / 8 emeralds to level 3" is a confusing thing to read when the emeralds are not what is holding
     * the stage back, so the line says what actually is.
     */
    public final String progressOverride;

    public OpenBrainNodeScreenPacket(int level, int maxLevel, int emeralds, int costNext,
                                     int villagers, int guards, int golems, int fertilized, int organic,
                                     int reputation, List<String> reflexLog, List<String> organicLog,
                                     List<String> reputationLog, String metalLine,
                                     String progressOverride) {
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
        this.metalLine = metalLine;
        this.progressOverride = progressOverride;
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
        buf.writeUtf(msg.metalLine);
        buf.writeUtf(msg.progressOverride);
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
        String metalLine = buf.readUtf();
        String progressOverride = buf.readUtf();
        return new OpenBrainNodeScreenPacket(level, maxLevel, emeralds, costNext, villagers, guards, golems,
                fertilized, organic, reputation, reflexLog, organicLog, reputationLog, metalLine, progressOverride);
    }

    public static void handle(OpenBrainNodeScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.BrainNodeScreenHooks.open(msg)));
        ctx.get().setPacketHandled(true);
    }
}
