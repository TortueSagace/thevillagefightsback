package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.villagefightsback.infection.StructureZones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Villagers fell the trees around their village and bank the timber as emeralds, so a village near a forest
 * climbs its stages faster than one on bare plains.
 *
 * <p>The economy only ever had two taps: the emeralds a village earns by itself and the ones a player feeds
 * it. A village that sits in a wood therefore had a resource it could not touch. Now one villager per pass
 * walks to the nearest natural tree, swings, and the trunk comes down bottom-up; each log is worth an emerald
 * to the node's own bank, which is what stages, walls, defenders and buildings are bought with.
 *
 * <p>Only a tree is felled, never a build: a log has to have leaves within a couple of blocks (so a player's
 * plank shack or a village wall is not a target), and nothing inside a claimed building zone is touched at
 * all. The logs are removed without drops -- the village keeps the timber, that is the point -- and the rate
 * is one tree per village every {@value #CHOP_COOLDOWN} ticks, so a forest thins out rather than vanishing.
 */
public final class VillagerLogging {
    private VillagerLogging() {}

    private static final int INTERVAL = 40;          // look for work every 2s
    private static final int CHOP_COOLDOWN = 200;    // ...but one felled tree per village per 10s
    private static final int WORKER_RADIUS = 32;      // villagers this close to the node count as its workers
    private static final int TREE_RANGE = 12;         // how far a worker reaches for a tree
    private static final int MAX_LOGS = 8;            // one trunk, not the whole canopy
    private static final int EMERALDS_PER_LOG = 1;

    /** When each village may fell its next tree. */
    private static final Map<GlobalPos, Long> NEXT_CHOP = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) return;

        BrainNodeData data = BrainNodeData.get(server);
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;

            long now = level.getGameTime();
            Long next = NEXT_CHOP.get(gp);
            if (next != null && now < next) continue;

            List<Villager> workers = level.getEntitiesOfClass(Villager.class,
                    new AABB(gp.pos()).inflate(WORKER_RADIUS), v -> v.isAlive());
            if (workers.isEmpty()) continue;

            Villager worker = workers.get(level.random.nextInt(workers.size()));
            int logs = fellNearestTree(level, worker);
            if (logs == 0) continue; // nothing worth felling in reach right now

            data.econ(gp).addEmeralds(logs * EMERALDS_PER_LOG);
            NEXT_CHOP.put(gp, now + CHOP_COOLDOWN);
        }
    }

    /**
     * Fell the trunk of the nearest natural tree within reach of {@code worker}. Returns the logs removed.
     *
     * <p>The nearest LOG is only a pointer: the trunk is then walked back down to the ground before cutting,
     * so the tree comes down from its base (the canopy follows by leaf decay) instead of leaving a floating
     * stump when the worker happened to spot the crown.
     */
    private static int fellNearestTree(ServerLevel level, Villager worker) {
        BlockPos origin = worker.blockPosition();
        BlockPos found = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos here : BlockPos.betweenClosed(
                origin.offset(-TREE_RANGE, -4, -TREE_RANGE), origin.offset(TREE_RANGE, 4, TREE_RANGE))) {
            if (!level.isLoaded(here)) continue;
            if (!level.getBlockState(here).is(BlockTags.LOGS)) continue;
            if (StructureZones.get(level).contains(here)) continue; // never chew the village's own build
            if (!nearLeaves(level, here)) continue;                 // a tree, not a plank
            double d = here.distSqr(origin);
            if (d < bestD) { bestD = d; found = here.immutable(); }
        }
        if (found == null) return 0;

        BlockPos.MutableBlockPos cursor = found.mutable();
        while (level.getBlockState(cursor.below()).is(BlockTags.LOGS)) cursor.move(Direction.DOWN);

        int cut = 0;
        while (cut < MAX_LOGS && level.getBlockState(cursor).is(BlockTags.LOGS)) {
            if (!level.destroyBlock(cursor.immutable(), false)) break;
            cut++;
            cursor.move(Direction.UP);
        }
        if (cut == 0) return 0;

        worker.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, worker.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 0.8F, 0.9F);
        return cut;
    }

    /** True if there is foliage close enough that this LOG is part of a growing tree rather than a build. */
    private static boolean nearLeaves(ServerLevel level, BlockPos log) {
        for (BlockPos here : BlockPos.betweenClosed(log.offset(-2, 0, -2), log.offset(2, 3, 2))) {
            BlockState state = level.getBlockState(here);
            if (state.is(BlockTags.LEAVES)) return true;
        }
        return false;
    }
}
