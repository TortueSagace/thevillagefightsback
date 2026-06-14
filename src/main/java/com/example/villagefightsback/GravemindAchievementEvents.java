package com.example.villagefightsback;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Grants the gravemind-stage advancements: every couple of seconds, each player near a village fires the
 * {@link GravemindStageTrigger} with that village's current stage. Advancements dedupe, so a player simply
 * earns the Settlement/Fertilization/Expansion/Culmination advancement the first time they are near a village
 * that has reached the matching stage -- however the village got there.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GravemindAchievementEvents {
    private GravemindAchievementEvents() {}

    private static final double RANGE = 96.0;
    private static final double RANGE_SQR = RANGE * RANGE;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 40 != 0) return;
        GravemindData data = GravemindData.get(server);
        if (data.blocks().isEmpty()) return;

        long _p = ModProfiler.now();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int best = maxNearbyStage(data, player);
            if (best > 0) ModTriggers.GRAVEMIND_STAGE.trigger(player, best);
        }
        ModProfiler.add("gravemind.advancements", _p);
    }

    /** The HIGHEST stage among graveminds near the player (same dimension, within range), or 0 if none. */
    private static int maxNearbyStage(GravemindData data, ServerPlayer player) {
        int max = 0;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(player.level().dimension())) continue;
            if (gp.pos().distToCenterSqr(player.getX(), player.getY(), player.getZ()) > RANGE_SQR) continue;
            max = Math.max(max, data.econ(gp).getLevel());
        }
        return max;
    }
}
