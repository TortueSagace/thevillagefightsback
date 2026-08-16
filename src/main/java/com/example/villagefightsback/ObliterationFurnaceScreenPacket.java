package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
 *
 * <p>1.21 note: main's plain class with an encode/decode pair, a numeric channel id and a {@code DistExecutor}
 * hop is now a {@code CustomPacketPayload} record with a STREAM_CODEC and a plain client-class call. The wire
 * content is unchanged -- a var-int of ticks then the list of names, each capped at 128 characters exactly as
 * main's {@code writeUtf(n, 128)} capped it. The dist guard is not lost either: the registrar only ever hands
 * this payload to a client handler, so the handler body is by construction client-only, exactly as the other
 * server-to-client payloads on this branch already are.
 */
public record ObliterationFurnaceScreenPacket(int burnTicks, List<String> names) implements CustomPacketPayload {
    public static final Type<ObliterationFurnaceScreenPacket> TYPE =
            new Type<>(TheVillageFightsBack.id("obliteration_furnace_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ObliterationFurnaceScreenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ObliterationFurnaceScreenPacket::burnTicks,
                    ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list()), ObliterationFurnaceScreenPacket::names,
                    ObliterationFurnaceScreenPacket::new);

    @Override
    public Type<ObliterationFurnaceScreenPacket> type() {
        return TYPE;
    }

    /** Build the snapshot for {@code at} and send it to {@code player}. */
    public static void open(Player player, GlobalPos at, ObliterationData data) {
        if (!(player instanceof ServerPlayer sp)) return;
        List<String> names = new ArrayList<>();
        for (ResourceLocation id : data.listFor(at)) {
            // getOptional, not get: BuiltInRegistries.ENTITY_TYPE is a DEFAULTED registry, so plain get hands
            // back minecraft:pig for an id nobody registered instead of null, and the "show the raw id when we
            // cannot resolve it" fallback below would silently become "show Pig". Same trap MCACompat documents.
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            names.add(type != null ? type.getDescription().getString() : id.toString());
        }
        names.sort(String::compareToIgnoreCase);
        ModNetworkBridge.sendToPlayer(sp, new ObliterationFurnaceScreenPacket(data.burnTicks(at), names));
    }

    public static void handle(ObliterationFurnaceScreenPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> com.example.villagefightsback.client.ObliterationFurnaceScreen
                .show(msg.burnTicks(), msg.names()));
    }

    /** Kept out of {@link com.example.villagefightsback.network.ModNetwork} so this class can live beside the
     *  block rather than in the network package. */
    static final class ModNetworkBridge {
        private ModNetworkBridge() {}

        static void sendToPlayer(ServerPlayer sp, ObliterationFurnaceScreenPacket msg) {
            com.example.villagefightsback.network.ModNetwork.sendTo(sp, msg);
        }
    }
}
