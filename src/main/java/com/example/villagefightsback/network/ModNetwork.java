package com.example.villagefightsback.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.villagefightsback.ObliterationFurnaceScreenPacket;
import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.platform.network.NetworkContext;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The mod's networking, on Fabric's play-channel API.
 *
 * <p>The Forge branch runs everything through one SimpleChannel where each message has a positional integer id,
 * with a warning never to insert into the middle because that silently renumbers the rest. Fabric names channels
 * instead, so each packet gets its own {@code thevillagefightsback:<name>} id and the ordering hazard is gone --
 * new packets can go anywhere in this list. Renaming one, though, breaks compatibility with older clients exactly
 * as renumbering did.
 *
 * <p>Every packet keeps its Forge-shaped {@code encode} / {@code decode} / {@code handle(msg, Supplier&lt;Context&gt;)}
 * trio; only the plumbing here changed.
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** One packet type: how to name it, write it, read it, and run it. */
    private record Def<T>(ResourceLocation id,
                          Class<T> type,
                          BiConsumer<T, FriendlyByteBuf> encoder,
                          Function<FriendlyByteBuf, T> decoder,
                          BiConsumer<T, Supplier<NetworkContext>> handler,
                          boolean toClient) {}

    private static final Map<Class<?>, Def<?>> BY_TYPE = new LinkedHashMap<>();
    private static final List<Def<?>> DEFS = new ArrayList<>();

    private static <T> void define(String name, Class<T> type,
                                   BiConsumer<T, FriendlyByteBuf> encoder,
                                   Function<FriendlyByteBuf, T> decoder,
                                   BiConsumer<T, Supplier<NetworkContext>> handler,
                                   boolean toClient) {
        Def<T> def = new Def<>(new ResourceLocation(TheVillageFightsBack.MODID, name), type,
                encoder, decoder, handler, toClient);
        DEFS.add(def);
        BY_TYPE.put(type, def);
    }

    /**
     * Declares every packet and hooks up the server-bound receivers. Called from the mod initializer, on both
     * sides -- the client needs the declarations too, so it knows how to encode what it sends.
     */
    public static void register() {
        if (!DEFS.isEmpty()) return;

        define("open_brain_node_screen", OpenBrainNodeScreenPacket.class,
                OpenBrainNodeScreenPacket::encode, OpenBrainNodeScreenPacket::decode,
                OpenBrainNodeScreenPacket::handle, true);
        define("emerald_tint", EmeraldTintPacket.class,
                EmeraldTintPacket::encode, EmeraldTintPacket::decode, EmeraldTintPacket::handle, true);
        define("screen_shake", ScreenShakePacket.class,
                ScreenShakePacket::encode, ScreenShakePacket::decode, ScreenShakePacket::handle, true);
        define("limbo_beam", LimboBeamPacket.class,
                LimboBeamPacket::encode, LimboBeamPacket::decode, LimboBeamPacket::handle, false);
        define("limbo_cooldown", LimboCooldownPacket.class,
                LimboCooldownPacket::encode, LimboCooldownPacket::decode, LimboCooldownPacket::handle, true);
        define("village_discovery", VillageDiscoveryPacket.class,
                VillageDiscoveryPacket::encode, VillageDiscoveryPacket::decode,
                VillageDiscoveryPacket::handle, true);
        define("spawn_hint", SpawnHintPacket.class,
                SpawnHintPacket::encode, SpawnHintPacket::decode, SpawnHintPacket::handle, true);
        define("obliteration_furnace_screen", ObliterationFurnaceScreenPacket.class,
                ObliterationFurnaceScreenPacket::encode, ObliterationFurnaceScreenPacket::decode,
                ObliterationFurnaceScreenPacket::handle, true);
        define("scythe_save", ScytheSavePacket.class,
                ScytheSavePacket::encode, ScytheSavePacket::decode, ScytheSavePacket::handle, true);
        define("open_mode_select", OpenModeSelectPacket.class,
                OpenModeSelectPacket::encode, OpenModeSelectPacket::decode, OpenModeSelectPacket::handle, true);
        define("choose_difficulty", ChooseDifficultyPacket.class,
                ChooseDifficultyPacket::encode, ChooseDifficultyPacket::decode,
                ChooseDifficultyPacket::handle, false);
        define("choose_mode", ChooseModePacket.class,
                ChooseModePacket::encode, ChooseModePacket::decode, ChooseModePacket::handle, false);
        define("open_reputation_book", OpenReputationBookPacket.class,
                OpenReputationBookPacket::encode, OpenReputationBookPacket::decode,
                OpenReputationBookPacket::handle, true);
        define("reversed_gravity", ReversedGravityPacket.class,
                ReversedGravityPacket::encode, ReversedGravityPacket::decode,
                ReversedGravityPacket::handle, true);

        for (Def<?> def : DEFS) {
            if (!def.toClient()) {
                registerServerReceiver(def);
            }
        }
    }

    private static <T> void registerServerReceiver(Def<T> def) {
        ServerPlayNetworking.registerGlobalReceiver(def.id(), (server, player, handler, buf, sender) -> {
            T msg = def.decoder().apply(buf);
            NetworkContext ctx = new NetworkContext(server, player);
            def.handler().accept(msg, () -> ctx);
        });
    }

    /**
     * The client half: receivers for every server-to-client packet. Called from the client initializer only, so a
     * dedicated server never touches the client networking classes.
     */
    public static void registerClientReceivers() {
        for (Def<?> def : DEFS) {
            if (def.toClient()) {
                registerClientReceiver(def);
            }
        }
    }

    private static <T> void registerClientReceiver(Def<T> def) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(def.id(),
                (client, handler, buf, sender) -> {
                    T msg = def.decoder().apply(buf);
                    NetworkContext ctx = new NetworkContext(client, null);
                    def.handler().accept(msg, () -> ctx);
                });
    }

    /** Send a packet to one player (server -> client). */
    public static void sendTo(ServerPlayer player, Object msg) {
        ServerPlayNetworking.send(player, idOf(msg), write(msg));
    }

    /** Send a packet to every client tracking {@code entity} (server -> clients), including the entity itself. */
    public static void sendToTracking(Entity entity, Object msg) {
        ResourceLocation id = idOf(msg);
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, id, write(msg));
        }
        // PlayerLookup.tracking does not include the entity itself when it is a player; Forge's
        // TRACKING_ENTITY_AND_SELF does, and several call sites rely on the sender seeing its own effect.
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, id, write(msg));
        }
    }

    /** Send a packet to every player currently in {@code dim} (server -> clients). */
    public static void sendToDimension(ResourceKey<Level> dim, Object msg) {
        net.minecraft.server.MinecraftServer server =
                com.example.villagefightsback.platform.ServerRef.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;
        ResourceLocation id = idOf(msg);
        for (ServerPlayer player : PlayerLookup.world(level)) {
            // A fresh buffer per recipient: a sent buffer is consumed and released, so one cannot be shared.
            ServerPlayNetworking.send(player, id, write(msg));
        }
    }

    /** Send a packet from the client to the server. */
    public static void sendToServer(Object msg) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(idOf(msg), write(msg));
    }

    private static ResourceLocation idOf(Object msg) {
        return def(msg).id();
    }

    @SuppressWarnings("unchecked")
    private static <T> FriendlyByteBuf write(Object msg) {
        Def<T> def = (Def<T>) def(msg);
        FriendlyByteBuf buf = PacketByteBufs.create();
        def.encoder().accept((T) msg, buf);
        return buf;
    }

    private static Def<?> def(Object msg) {
        Def<?> def = BY_TYPE.get(msg.getClass());
        if (def == null) {
            throw new IllegalArgumentException("Unregistered packet type " + msg.getClass().getName()
                    + "; add it to ModNetwork.register()");
        }
        return def;
    }
}
