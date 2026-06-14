package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: the wearer's banish-ability cooldown (ticks remaining), for the HUD indicator. */
public class LimboCooldownPacket {
    public final int ticks;

    public LimboCooldownPacket(int ticks) {
        this.ticks = ticks;
    }

    public static void encode(LimboCooldownPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ticks);
    }

    public static LimboCooldownPacket decode(FriendlyByteBuf buf) {
        return new LimboCooldownPacket(buf.readVarInt());
    }

    public static void handle(LimboCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.LimboAbilityClient.setCooldown(msg.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
