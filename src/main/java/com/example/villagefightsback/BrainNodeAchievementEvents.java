package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.SubscribeEvent;
import com.example.villagefightsback.platform.ServerRef;

/**
 * Grants the brain node-stage advancements: every couple of seconds, each player near a village fires the
 * {@link BrainNodeStageTrigger} with that village's current stage. Advancements dedupe, so a player simply
 * earns the Settlement/Fertilization/Expansion/Culmination advancement the first time they are near a village
 * that has reached the matching stage -- however the village got there.
 */
public final class BrainNodeAchievementEvents {
    private BrainNodeAchievementEvents() {}

    private static final double RANGE = 96.0;
    private static final double RANGE_SQR = RANGE * RANGE;
    private static final int PARTY_COUNT = 10;          // "Emerald party"
    private static final int OVERPOPULATION_COUNT = 50; // "Overpopulation" (the default army cap)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerRef.getCurrentServer();
        if (server == null || server.getTickCount() % 40 != 0) return;
        BrainNodeData data = BrainNodeData.get(server);
        if (data.blocks().isEmpty()) return;

        long _p = ModProfiler.now();
        for (GlobalPos gp : data.blocks()) {
            // Players near THIS brain (same dimension, within range).
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!gp.dimension().equals(player.level().dimension())) continue;
                if (gp.pos().distToCenterSqr(player.getX(), player.getY(), player.getZ()) > RANGE_SQR) continue;
                near.add(player);
            }
            if (near.isEmpty()) continue; // nobody to award -> skip the entity scan entirely

            int stage = data.econ(gp).getLevel();
            int emeralds = countEmeraldUnits(server, gp); // emerald units around this village
            for (ServerPlayer player : near) {
                if (stage > 0) ModTriggers.BRAIN_NODE_STAGE.trigger(player, stage); // dedupes; highest nearby satisfies
                if (emeralds >= PARTY_COUNT) Achievements.award(player, "brain_node/emerald_party");
                if (emeralds >= OVERPOPULATION_COUNT) Achievements.award(player, "brain_node/overpopulation");
            }
        }
        ModProfiler.add("brain_node.advancements", _p);
    }

    /** Count the emerald-faction units within {@link #RANGE} of a brain node (0 if its dimension is not loaded). */
    private static int countEmeraldUnits(MinecraftServer server, GlobalPos gp) {
        ServerLevel level = server.getLevel(gp.dimension());
        if (level == null) return 0;
        AABB box = new AABB(gp.pos()).inflate(RANGE);
        return level.getEntitiesOfClass(Mob.class, box, Factions::isEmerald).size();
    }
}
