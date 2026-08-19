package com.example.villagefightsback.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.DistExecutor;
import com.example.villagefightsback.platform.network.NetworkContext;

/** Server -> client: shake the camera for {@code durationTicks} at {@code intensity} (degrees of jitter). */
public class ScreenShakePacket {
    public final float intensity;
    public final int durationTicks;

    public ScreenShakePacket(float intensity, int durationTicks) {
        this.intensity = intensity;
        this.durationTicks = durationTicks;
    }

    public static void encode(ScreenShakePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.intensity);
        buf.writeVarInt(msg.durationTicks);
    }

    public static ScreenShakePacket decode(FriendlyByteBuf buf) {
        return new ScreenShakePacket(buf.readFloat(), buf.readVarInt());
    }

    public static void handle(ScreenShakePacket msg, Supplier<NetworkContext> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.ScreenShakeClient.add(msg.intensity, msg.durationTicks)));
        ctx.get().setPacketHandled(true);
    }
}
