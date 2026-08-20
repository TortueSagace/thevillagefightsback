package com.example.villagefightsback.network;

import java.util.function.Supplier;

import com.example.villagefightsback.Difficulty;
import com.example.villagefightsback.HostileStart;
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
 * Client -&gt; server: the answer to the difficulty screen, which follows the game-mode screen.
 *
 * <p>Deliberately a sibling of {@link ChooseModePacket} rather than an extra field on it. The two screens are
 * answered a moment apart and each is entitled to be answered on its own; folding them together would mean the
 * mode could not be recorded until the difficulty was too, and a disconnect between the two screens would lose
 * both answers instead of one.
 */
public class ChooseDifficultyPacket {
    public final int difficulty;

    public ChooseDifficultyPacket(int difficulty) {
        this.difficulty = difficulty;
    }

    public static void encode(ChooseDifficultyPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.difficulty);
    }

    public static ChooseDifficultyPacket decode(FriendlyByteBuf buf) {
        return new ChooseDifficultyPacket(buf.readVarInt());
    }

    public static void handle(ChooseDifficultyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            if (!WorldModeEvents.canChoose(player)) return;    // not entitled to answer
            WorldModeData data = WorldModeData.get(server);
            if (data.chosenDifficulty() != null) return;       // somebody answered first; first answer wins

            // byId never returns null: an unrecognised number plays Normal rather than locking the world out.
            Difficulty chosen = Difficulty.byId(msg.difficulty);
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
            // ...and the opening kit, for the same reason: at login the answers did not exist, so a Survival
            // player would have been handed the Creative bell before anyone said which world this was.
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                com.example.villagefightsback.StartingItems.grantStarterKit(p);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
