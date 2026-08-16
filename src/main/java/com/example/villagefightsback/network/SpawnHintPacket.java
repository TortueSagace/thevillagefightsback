package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -&gt; client: show one of the bottom-of-screen hints for {@code ticks}.
 *
 * <p>Carries a duration and a LINE NUMBER, never the line. Like {@link VillageDiscoveryPacket}, the text is a
 * constant on the client, so nothing but an int travels; adding a hint means adding a string to
 * {@code VillageDiscoveryHud} and a name to the constants below, and the wire format never changes.
 */
public class SpawnHintPacket {
    /** The starter bell, shown once to a player who spawned away from any village. */
    public static final int HINT_BELL = 0;
    /** A rich block was mined: the village's people are the ones who know how to get emeralds out of it. */
    public static final int HINT_RICH_VILLAGERS = 1;
    /** A rich block was mined: the rarer line, pointing at the Obliteration Furnace instead. */
    public static final int HINT_RICH_OBLITERATE = 2;

    public final int ticks;
    public final int id;

    /** The starter-bell hint, or 0 ticks to hide whatever is showing. */
    public SpawnHintPacket(int ticks) {
        this(ticks, HINT_BELL);
    }

    public SpawnHintPacket(int ticks, int id) {
        this.ticks = ticks;
        this.id = id;
    }

    public static void encode(SpawnHintPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ticks);
        buf.writeVarInt(msg.id);
    }

    public static SpawnHintPacket decode(FriendlyByteBuf buf) {
        return new SpawnHintPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SpawnHintPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.VillageDiscoveryHud.showHint(msg.ticks, msg.id)));
        ctx.get().setPacketHandled(true);
    }
}
