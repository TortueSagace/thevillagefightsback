package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import com.example.villagefightsback.platform.Dist;
import com.example.villagefightsback.platform.DistExecutor;
import com.example.villagefightsback.platform.network.NetworkContext;
import com.example.villagefightsback.platform.ModRegistries;

/**
 * Server -&gt; client: everything the Obliteration Furnace's screen shows.
 *
 * <p>A one-shot snapshot rather than a live container. The screen is a READING of the furnace -- how long it
 * has left, what it currently holds erased -- and nothing on it can be clicked, so there is nothing to keep in
 * sync and no reason to pay for a menu, its slots, and its per-tick sync. Fuel goes in by right-clicking the
 * block itself, which reopens the screen with fresh numbers.
 *
 * <p>The DISPLAY NAMES travel, not the entity ids. Resolving {@code minecraft:zombie} to "Zombie" needs the
 * registry and the language file, and doing it here means the screen can render a list of creatures without
 * caring whether the client has every mod that contributed one.
 */
public class ObliterationFurnaceScreenPacket {
    public final int burnTicks;
    public final List<String> names;

    public ObliterationFurnaceScreenPacket(int burnTicks, List<String> names) {
        this.burnTicks = burnTicks;
        this.names = names;
    }

    /** Build the snapshot for {@code at} and send it to {@code player}. */
    public static void open(Player player, GlobalPos at, ObliterationData data) {
        if (!(player instanceof ServerPlayer sp)) return;
        List<String> names = new ArrayList<>();
        for (ResourceLocation id : data.listFor(at)) {
            EntityType<?> type = ModRegistries.ENTITY_TYPES.getValue(id);
            names.add(type != null ? type.getDescription().getString() : id.toString());
        }
        names.sort(String::compareToIgnoreCase);
        ModNetworkBridge.sendToPlayer(sp, new ObliterationFurnaceScreenPacket(data.burnTicks(at), names));
    }

    public static void encode(ObliterationFurnaceScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.burnTicks);
        buf.writeVarInt(msg.names.size());
        for (String n : msg.names) buf.writeUtf(n, 128);
    }

    public static ObliterationFurnaceScreenPacket decode(FriendlyByteBuf buf) {
        int ticks = buf.readVarInt();
        int n = buf.readVarInt();
        List<String> names = new ArrayList<>(n);
        for (int i = 0; i < n; i++) names.add(buf.readUtf(128));
        return new ObliterationFurnaceScreenPacket(ticks, names);
    }

    public static void handle(ObliterationFurnaceScreenPacket msg, Supplier<NetworkContext> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.villagefightsback.client.ObliterationFurnaceScreen
                        .show(msg.burnTicks, msg.names)));
        ctx.get().setPacketHandled(true);
    }

    /** Kept out of {@link ModNetwork} so this class can live beside the block rather than in the network package. */
    static final class ModNetworkBridge {
        private ModNetworkBridge() {}

        static void sendToPlayer(ServerPlayer sp, ObliterationFurnaceScreenPacket msg) {
            com.example.villagefightsback.network.ModNetwork.sendTo(sp, msg);
        }
    }
}
