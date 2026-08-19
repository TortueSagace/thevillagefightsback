package com.example.villagefightsback;

import com.example.villagefightsback.network.ModNetwork;
import com.example.villagefightsback.network.OpenModeSelectPacket;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the one-off "choose your mode" screen and the per-world override it produces.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link LevelEvent.CreateSpawnPosition} fires exactly once in a world's life, while its initial spawn is
 *       being picked, so it is the precise signal for "this world is brand new";</li>
 *   <li>on server start we snapshot the .toml's own infection flags (so they can be put back later) and, if the
 *       world already holds an answer, push that answer over them;</li>
 *   <li>the first player who is allowed to answer gets the screen sent to them as they join;</li>
 *   <li>on server stop the .toml values are restored, so the next world starts from the file rather than from
 *       whatever the previous world happened to choose.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldModeEvents {
    private WorldModeEvents() {}

    /**
     * A world's initial spawn was just generated, so the world is new. Set during level creation, which happens
     * before the server has finished starting; {@link #onServerStarted} consumes it once the data storage is safe
     * to touch.
     */
    private static boolean freshWorld;

    /**
     * How much played time still counts as "just created" for the fallback below, in ticks (60s). Only consulted
     * when the world has no stored data at all, so it can never re-prompt an established save.
     */
    private static final long FRESH_GAME_TIME = 1200L;

    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        freshWorld = true;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Config.snapshotInfectionFlags();

        boolean fresh = freshWorld;
        freshWorld = false;
        // Fallback for the world types that set their spawn without firing the event above (and for a world
        // created while the mod was absent on the very first tick): no stored data AND essentially no play time.
        if (!fresh && WorldModeData.getExisting(server) == null
                && server.overworld().getGameTime() < FRESH_GAME_TIME) {
            fresh = true;
        }

        WorldModeData data = WorldModeData.get(server);
        if (fresh) {
            data.markPending();
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] new world -- the game-mode screen will open for the first eligible player.");
        }
        data.pushToConfig();
        if (data.isChosen()) {
            WorldMode mode = data.currentMode();
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] world mode: {} (block={}, mob={}, infestation={}).",
                    mode != null ? mode.title() : "custom",
                    Config.blockInfectionEnabled(), Config.mobInfectionEnabled(), Config.emeraldInfestationEnabled());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Config.restoreInfectionFlags();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        WorldModeData data = WorldModeData.get(server);
        // Ask whenever setup is UNFINISHED, which is not the same as "the mode is unanswered". The old test was
        // `isPending() && !isChosen()`, and answering the mode clears pending -- so a player who answered the
        // mode but never the difficulty was never asked again, and the world stayed held forever. An
        // established save has setupPending false and is still never prompted.
        if (!data.isSetupPending()) return;
        if (!canChoose(player)) return;                   // a guest on a dedicated server just plays the host's mode
        ModNetwork.sendTo(player, new OpenModeSelectPacket());
    }

    /**
     * Who gets asked: the singleplayer host, or an operator on a dedicated server (where the world was created
     * with nobody online, so the first op to join answers for everyone).
     */
    public static boolean canChoose(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        return server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2);
    }

    /**
     * Reconcile the loaded world's mode with the config file after a reload: a hand edit of one of the
     * three infection flags is adopted, and anything untouched keeps the world's own answer.
     */
    public static void reapplyToRunningServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        WorldModeData data = WorldModeData.getExisting(server);
        if (data != null) data.reconcileFromConfig();
    }
}
