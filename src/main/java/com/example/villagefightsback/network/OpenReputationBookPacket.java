package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.DistExecutor;
import com.example.villagefightsback.platform.network.NetworkContext;

/**
 * Server -&gt; client: open the Reputation Book.
 *
 * <p>Carries the difficulty and the reader's current standing, and not one word of the book. The text is a
 * client-side constant, like {@link SpawnHintPacket}'s lines -- but the DIFFICULTY has to travel, because it
 * lives in a COMMON {@code ModConfigSpec} and Forge does not sync those to the client. Without it a
 * single-player client would read the right book by luck and a multiplayer client would read Neutral's.
 */
public class OpenReputationBookPacket {
    public final int difficulty;
    public final int reputation;

    public OpenReputationBookPacket(int difficulty, int reputation) {
        this.difficulty = difficulty;
        this.reputation = reputation;
    }

    public static void encode(OpenReputationBookPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.difficulty);
        buf.writeInt(msg.reputation);          // signed: standing goes below zero
    }

    public static OpenReputationBookPacket decode(FriendlyByteBuf buf) {
        return new OpenReputationBookPacket(buf.readVarInt(), buf.readInt());
    }

    public static void handle(OpenReputationBookPacket msg, Supplier<NetworkContext> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.ReputationBookScreen.show(
                        msg.difficulty, msg.reputation)));
        ctx.get().setPacketHandled(true);
    }
}
