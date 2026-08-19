package com.example.villagefightsback.platform.event;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.platform.EventBus;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

/**
 * Where the shim events are fired from -- the half that Fabric API already provides a callback for.
 *
 * <p>The rest are fired from mixins in {@code com.example.villagefightsback.mixin}, because Fabric offers no hook
 * at all (a mob taking a new target), or offers one that cannot do what the handlers need (Fabric's damage
 * callback votes yes or no, while the mod's handlers change the damage amount).
 *
 * <p>Every cancellable event fired here has its cancellation acted on. If that ever stops being true, the mod
 * goes quietly wrong rather than loudly.
 */
public final class ModEventSources {
    private ModEventSources() {}

    /**
     * Set while {@code ServerLevel.addEntity} is running, so the ENTITY_LOAD callback below can tell a fresh
     * spawn (already announced, cancellably, by the mixin) from an entity coming back off disk.
     */
    public static Entity freshlyAdded;

    public static void init() {
        ticks();
        lifecycle();
        entities();
        interactions();
        commands();
        trades();
    }

    private static void ticks() {
        ServerTickEvents.START_SERVER_TICK.register(server ->
                EventBus.post(new TickEvent.ServerTickEvent(TickEvent.Phase.START, server)));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                EventBus.post(new TickEvent.ServerTickEvent(TickEvent.Phase.END, server)));
    }

    private static void lifecycle() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                EventBus.post(new ServerStartedEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                EventBus.post(new ServerStoppingEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                EventBus.post(new ServerStoppedEvent(server)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                EventBus.post(new PlayerEvent.PlayerLoggedInEvent(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                EventBus.post(new PlayerEvent.PlayerLoggedOutEvent(handler.getPlayer())));

        // Forge's PlayerEvent.Clone: a new player entity replacing an old one. alive == false means a death.
        // Forge also carries the death-surviving slice of the persistent tag across for you; nothing on Fabric
        // does, so it is copied here, before the handlers run and find it missing.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            com.example.villagefightsback.platform.PersistentData
                    .copyPersistedAcrossRespawn(oldPlayer, newPlayer);
            EventBus.post(new PlayerEvent.Clone(newPlayer, oldPlayer, !alive));
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                EventBus.post(new PlayerEvent.PlayerChangedDimensionEvent(
                        player, origin.dimension(), destination.dimension())));

        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                EventBus.post(new ChunkEvent.Load(chunk, level)));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                EventBus.post(new ChunkEvent.Unload(chunk, level)));
    }

    private static void entities() {
        // A fresh spawn is announced by the ServerLevel.addEntity mixin, which can still call it off. What is left
        // here is entities coming back off disk, which cannot be refused -- they are already in the save.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity == freshlyAdded) return;
            EventBus.post(new EntityJoinLevelEvent(entity, level, true));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
                EventBus.post(new EntityLeaveLevelEvent(entity, level)));

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) ->
                EventBus.post(new PlayerEvent.StartTracking(player, trackedEntity)));

        // Fired before the entity actually dies, so cancelling keeps it alive -- Forge's semantics.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !EventBus.post(new LivingDeathEvent(entity, source)).isCanceled());
    }

    private static void interactions() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !EventBus.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled());

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            PlayerInteractEvent.LeftClickBlock event =
                    EventBus.post(new PlayerInteractEvent.LeftClickBlock(player, pos, direction));
            // FAIL stops the break outright, which is what cancelling the Forge event does.
            return event.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            PlayerInteractEvent.RightClickBlock event = EventBus.post(new PlayerInteractEvent.RightClickBlock(
                    player, hand, hitResult.getBlockPos(), hitResult.getDirection()));
            return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            PlayerInteractEvent.EntityInteractSpecific event =
                    EventBus.post(new PlayerInteractEvent.EntityInteractSpecific(player, hand, entity));
            return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
        });
    }

    private static void commands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EventBus.post(new RegisterCommandsEvent(dispatcher, registryAccess)));
    }

    /**
     * Forge hands each profession's whole trade table to listeners once, during setup. Fabric instead takes
     * per-profession, per-level listings. So the event is fired here with an empty table, and whatever the
     * handlers put in it is passed straight on to Fabric -- which works because all three handlers only ever add.
     */
    private static void trades() {
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            Int2ObjectMap<List<VillagerTrades.ItemListing>> table = new Int2ObjectOpenHashMap<>();
            for (int level = 1; level <= 5; level++) {
                table.put(level, new ArrayList<>());
            }
            EventBus.post(new VillagerTradesEvent(table, profession));
            for (int level = 1; level <= 5; level++) {
                List<VillagerTrades.ItemListing> listings = table.get(level);
                if (listings == null || listings.isEmpty()) continue;
                List<VillagerTrades.ItemListing> copy = List.copyOf(listings);
                TradeOfferHelper.registerVillagerOffers(profession, level, factories -> factories.addAll(copy));
            }
        }
    }
}
