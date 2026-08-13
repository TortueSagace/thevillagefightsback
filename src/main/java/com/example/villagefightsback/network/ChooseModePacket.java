package com.example.villagefightsback.network;

import java.util.function.Supplier;

import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.WorldMode;
import com.example.villagefightsback.WorldModeData;
import com.example.villagefightsback.WorldModeEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server: the answer to the mode screen. The mode id is re-validated here (an id off the network is
 * never trusted), as is the sender's right to answer and the fact that nobody has answered already.
 */
public class ChooseModePacket {
    public final int mode;

    public ChooseModePacket(int mode) {
        this.mode = mode;
    }

    public static void encode(ChooseModePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
    }

    public static ChooseModePacket decode(FriendlyByteBuf buf) {
        return new ChooseModePacket(buf.readVarInt());
    }

    public static void handle(ChooseModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            WorldMode chosen = WorldMode.byId(msg.mode);
            if (chosen == null) return;                       // not one of the three presets
            if (!WorldModeEvents.canChoose(player)) return;    // not entitled to answer
            WorldModeData data = WorldModeData.get(server);
            if (data.isChosen()) return;                       // somebody answered first; first answer wins

            data.choose(chosen);
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] world mode set to \"{}\" by {}.",
                    chosen.title(), player.getGameProfile().getName());
            player.sendSystemMessage(Component.literal("[The Village Fights Back] Mode: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(chosen.title()).withStyle(ChatFormatting.GREEN)));
        });
        ctx.get().setPacketHandled(true);
    }
}
