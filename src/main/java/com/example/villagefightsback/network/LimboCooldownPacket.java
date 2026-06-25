package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: the wearer's banish-ability cooldown (ticks remaining), for the HUD indicator. */
public record LimboCooldownPacket(int ticks) implements CustomPacketPayload {
    public static final Type<LimboCooldownPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("limbo_cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LimboCooldownPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, LimboCooldownPacket::ticks,
                    LimboCooldownPacket::new);

    @Override
    public Type<LimboCooldownPacket> type() {
        return TYPE;
    }

    public static void handle(LimboCooldownPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                com.example.villagefightsback.client.LimboAbilityClient.setCooldown(msg.ticks()));
    }
}
