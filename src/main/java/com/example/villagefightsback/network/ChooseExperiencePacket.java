package com.example.villagefightsback.network;

import java.util.function.Supplier;

import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.WorldModeData;
import com.example.villagefightsback.WorldModeEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -&gt; server: the answer to the experience screen, asked between the mode and difficulty screens.
 *
 * <p>A sibling of {@link ChooseModePacket} and {@link ChooseDifficultyPacket} for the same reason they are
 * siblings of each other: each screen is answered on its own, and a disconnect between two of them should
 * lose one answer rather than all of them.
 *
 * <p>On the Creative path the client follows this immediately with a {@link ChooseDifficultyPacket} carrying
 * Neutral, because Creative skips the difficulty screen but the world still needs an answer to start.
 */
public class ChooseExperiencePacket {
    public final boolean survival;

    public ChooseExperiencePacket(boolean survival) {
        this.survival = survival;
    }

    public static void encode(ChooseExperiencePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.survival);
    }

    public static ChooseExperiencePacket decode(FriendlyByteBuf buf) {
        return new ChooseExperiencePacket(buf.readBoolean());
    }

    public static void handle(ChooseExperiencePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            if (!WorldModeEvents.canChoose(player)) return;    // not entitled to answer
            WorldModeData data = WorldModeData.get(server);
            if (data.chosenDifficulty() != null) return;       // the world is already set up; too late

            data.chooseExperience(msg.survival);
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] experience set to \"{}\" by {}.",
                    msg.survival ? "Survival" : "Creative", player.getGameProfile().getName());
            player.sendSystemMessage(Component.literal("[The Village Fights Back] Experience: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(msg.survival ? "Survival" : "Creative")
                            .withStyle(msg.survival ? ChatFormatting.GOLD : ChatFormatting.GREEN)));
        });
        ctx.get().setPacketHandled(true);
    }
}
