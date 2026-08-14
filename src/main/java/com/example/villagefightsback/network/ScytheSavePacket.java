package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -&gt; client: play the totem-of-undying save animation, but holding the Limbo Scythe.
 *
 * <p>Carries nothing. The whole message is "it happened to you"; the scythe is a constant on the client, so
 * there is no reason to put an item on the wire.
 *
 * <p>A packet rather than {@code broadcastEntityEvent(entity, 35)} because that vanilla event hard-codes the
 * totem: it emits totem particles and calls {@code displayItemActivation} with a totem stack, and the item
 * shown is exactly the part being replaced here. The screen animation is client-only anyway
 * ({@code GameRenderer#displayItemActivation}), so it has to be asked for rather than broadcast.
 */
public class ScytheSavePacket {

    public static void encode(ScytheSavePacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static ScytheSavePacket decode(FriendlyByteBuf buf) {
        return new ScytheSavePacket();
    }

    public static void handle(ScytheSavePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.ScytheSaveFx.play()));
        ctx.get().setPacketHandled(true);
    }
}
