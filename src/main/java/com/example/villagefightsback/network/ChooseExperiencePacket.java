package com.example.villagefightsback.network;

import com.example.villagefightsback.TheVillageFightsBack;
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
 * Client -&gt; server: the answer to the experience screen, asked between the mode and difficulty screens.
 *
 * <p>A sibling of {@link ChooseModePacket} and {@link ChooseDifficultyPacket} for the same reason they are
 * siblings of each other: each screen is answered on its own, and a disconnect between two of them should
 * lose one answer rather than all of them.
 */
public record ChooseExperiencePacket(boolean survival) implements CustomPacketPayload {
    public static final Type<ChooseExperiencePacket> TYPE =
            new Type<>(TheVillageFightsBack.id("choose_experience"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseExperiencePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ChooseExperiencePacket::survival,
                    ChooseExperiencePacket::new);

    @Override
    public Type<ChooseExperiencePacket> type() {
        return TYPE;
    }

    public static void handle(ChooseExperiencePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            if (!WorldModeEvents.canChoose(player)) return;    // not entitled to answer
            WorldModeData data = WorldModeData.get(server);
            if (data.chosenDifficulty() != null) return;       // the world is already set up; too late

            data.chooseExperience(msg.survival());
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] experience set to \"{}\" by {}.",
                    msg.survival() ? "Survival" : "Creative", player.getGameProfile().getName());
            player.sendSystemMessage(Component.literal("[The Village Fights Back] Experience: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(msg.survival() ? "Survival" : "Creative")
                            .withStyle(msg.survival() ? ChatFormatting.GOLD : ChatFormatting.GREEN)));
        });
    }
}
