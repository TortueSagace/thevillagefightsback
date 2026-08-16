package com.example.villagefightsback.network;

import com.example.villagefightsback.Difficulty;
import com.example.villagefightsback.HostileStart;
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
 * Client -&gt; server: the answer to the difficulty screen, which follows the game-mode screen.
 *
 * <p>Deliberately a sibling of {@link ChooseModePacket} rather than an extra field on it. The two screens are
 * answered a moment apart and each is entitled to be answered on its own; folding them together would mean the
 * mode could not be recorded until the difficulty was too, and a disconnect between the two screens would lose
 * both answers instead of one.
 *
 * <p>1.21 note: Forge's encode/decode/handle triple on a plain class becomes a {@link CustomPacketPayload}
 * record with a {@code Type} and a {@link StreamCodec}, exactly as its sibling {@link ChooseModePacket} does on
 * this branch. {@code ctx.get().getSender()} becomes {@code context.player()}, which on a server-bound payload
 * is always the sending {@link ServerPlayer} -- same value, same "never trust the client" checks below -- and
 * {@code setPacketHandled} no longer exists, the registrar knows the payload was handled.
 */
public record ChooseDifficultyPacket(int difficulty) implements CustomPacketPayload {
    public static final Type<ChooseDifficultyPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("choose_difficulty"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseDifficultyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ChooseDifficultyPacket::difficulty,
                    ChooseDifficultyPacket::new);

    @Override
    public Type<ChooseDifficultyPacket> type() {
        return TYPE;
    }

    public static void handle(ChooseDifficultyPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            if (!WorldModeEvents.canChoose(player)) return;    // not entitled to answer
            WorldModeData data = WorldModeData.get(server);
            if (data.chosenDifficulty() != null) return;       // somebody answered first; first answer wins

            // byId never returns null: an unrecognised number plays Normal rather than locking the world out.
            Difficulty chosen = Difficulty.byId(msg.difficulty());
            data.chooseDifficulty(chosen);
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] difficulty set to \"{}\" by {}.",
                    chosen.title(), player.getGameProfile().getName());
            player.sendSystemMessage(Component.literal("[The Village Fights Back] Difficulty: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(chosen.title()).withStyle(chosen.colour())));

            // THIS is where the hostile opening happens on a fresh world. It cannot be done at login: the
            // player is already in the world when this screen appears, so at login the answer did not exist
            // and the difficulty still read Normal. Every player online gets it, not just the one who
            // answered, since the answer applies to the whole world.
            for (ServerPlayer p : server.getPlayerList().getPlayers()) HostileStart.apply(p);
        });
    }
}
