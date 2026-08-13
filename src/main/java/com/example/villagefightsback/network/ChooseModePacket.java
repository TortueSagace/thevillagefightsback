package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.WorldMode;
import com.example.villagefightsback.WorldModeData;
import com.example.villagefightsback.WorldModeEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: the answer to the mode screen. The mode id is re-validated here (an id off the network is
 * never trusted), as is the sender's right to answer and the fact that nobody has answered already.
 *
 * <p>1.21 note: Forge's {@code ctx.getSender()} becomes {@code context.player()}, which on a server-bound
 * payload is always the sending {@link ServerPlayer} -- same value, same "never trust the client" checks below.
 */
public record ChooseModePacket(int mode) implements CustomPacketPayload {
    public static final Type<ChooseModePacket> TYPE =
            new Type<>(TheVillageFightsBack.id("choose_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ChooseModePacket::mode,
                    ChooseModePacket::new);

    @Override
    public Type<ChooseModePacket> type() {
        return TYPE;
    }

    public static void handle(ChooseModePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            WorldMode chosen = WorldMode.byId(msg.mode());
            if (chosen == null) return;                        // not one of the three presets
            if (!WorldModeEvents.canChoose(player)) return;     // not entitled to answer
            WorldModeData data = WorldModeData.get(server);
            if (data.isChosen()) return;                        // somebody answered first; first answer wins

            data.choose(chosen);
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] world mode set to \"{}\" by {}.",
                    chosen.title(), player.getGameProfile().getName());
            player.sendSystemMessage(Component.literal("[The Village Fights Back] Mode: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(chosen.title()).withStyle(ChatFormatting.GREEN)));
        });
    }
}
