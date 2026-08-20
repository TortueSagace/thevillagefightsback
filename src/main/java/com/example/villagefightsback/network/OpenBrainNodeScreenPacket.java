package com.example.villagefightsback.network;

import java.util.List;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: open the Village Brain Node upgrade screen, carrying the clicked village's current
 * level and emerald progress (the client has no access to the server-side {@code BrainNodeData}).
 */
public record OpenBrainNodeScreenPacket(
        int level,
        int maxLevel,
        int emeralds,
        int costNext, // -1 if already at max level
        int villagers, // defenders within the brain node's radius, by type
        int guards,
        int golems,
        int fertilized, // count of fertilized (fertile/rich) blocks in this dimension
        int organic, // banked organic mass (1.1.2 organic system; 0 / hidden when that mode is off)
        int reputation, // the OPENING player's standing with the village faction (global, not per node)
        List<String> reflexLog, // the brain's last (up to 10) reflex (emerald-spend) log lines, oldest first
        List<String> organicLog, // the brain's last (up to 10) organic-spawn decisions, oldest first
        List<String> reputationLog, // the last (up to 10) reputation changes, world-wide, oldest first
        /**
         * SURVIVAL only: the metal tribute line drawn under the emerald row, ready to draw. Empty in a
         * Creative world, which is also how the screen knows to keep its old height. Preformatted on the
         * server: the numbers and colours are decided there, and one string beats threading six counters
         * through two packet formats and duplicating the rule client-side where it could drift.
         */
        String metalLine,
        /**
         * SURVIVAL only: replaces the emerald progress line when the emeralds are already in and only metal
         * is missing. Empty otherwise -- "12 / 8 emeralds to level 3" is a confusing thing to read when the
         * emeralds are not what is holding the stage back.
         */
        String progressOverride
) implements CustomPacketPayload {
    public static final Type<OpenBrainNodeScreenPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("open_brain_node_screen"));

    // 13 fields exceeds StreamCodec.composite's max arity, so the codec is written out by hand.
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBrainNodeScreenPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenBrainNodeScreenPacket::write, OpenBrainNodeScreenPacket::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.level);
        buf.writeVarInt(this.maxLevel);
        buf.writeVarInt(this.emeralds);
        buf.writeVarInt(this.costNext);
        buf.writeVarInt(this.villagers);
        buf.writeVarInt(this.guards);
        buf.writeVarInt(this.golems);
        buf.writeVarInt(this.fertilized);
        buf.writeVarInt(this.organic);
        // Signed, not a var-int: standing goes below zero, and a var-int spends five bytes on every negative.
        buf.writeInt(this.reputation);
        buf.writeCollection(this.reflexLog, (b, s) -> b.writeUtf(s));
        buf.writeCollection(this.organicLog, (b, s) -> b.writeUtf(s));
        buf.writeCollection(this.reputationLog, (b, s) -> b.writeUtf(s));
        buf.writeUtf(this.metalLine);
        buf.writeUtf(this.progressOverride);
    }

    private static OpenBrainNodeScreenPacket read(RegistryFriendlyByteBuf buf) {
        return new OpenBrainNodeScreenPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readInt(),
                buf.readList(b -> b.readUtf()), buf.readList(b -> b.readUtf()), buf.readList(b -> b.readUtf()),
                buf.readUtf(), buf.readUtf());
    }

    @Override
    public Type<OpenBrainNodeScreenPacket> type() {
        return TYPE;
    }

    public static void handle(OpenBrainNodeScreenPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.BrainNodeScreenHooks.open(msg));
    }
}
