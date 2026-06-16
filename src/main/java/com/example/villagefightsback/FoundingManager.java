package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.example.villagefightsback.goal.FoundingSettlerGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * "Founding" a village from an empty brainNode. When the brain node is about to mark out its wall but no
 * villager is nearby, this dispatches 7 SETTLERS (evenly-spaced ring at radius 10, with an air-bubble spot search +
 * radius-3 fallback) and 4 guards. Each settler scatters for 2s (keeping apart) then lights a Blue TNT
 * to build a house ({@link FoundingSettlerGoal}). ~1s after the blast the wall plan is created and the
 * settlers -- now ordinary nearby villagers -- are promoted to the wall builders by the normal flow
 * (no new villagers are spawned).
 *
 * Wall-planning is GATED via {@link #gate} (called from BrainNodeEvents.driveWalls) so the wall isn't
 * marked out until founding completes.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FoundingManager {
    private FoundingManager() {}

    public static final String NBT_FOUNDING = "vgun_founding";

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");
    private static final double CHECK_RADIUS = 64.0;  // "no villager in the brain node's radius"
    private static final int SETTLERS = 7;
    private static final int HEX_RADIUS = 10;
    private static final int FALLBACK_RADIUS = 3;
    private static final int BUBBLE_SEARCH = 5;        // search this far for a 1x1x2 air bubble
    private static final int GUARDS = 4;
    private static final int SCATTER_TICKS = 20;       // settlers run for 1s before dropping the TNT
    private static final int POST_EXPLODE_TICKS = 20;  // wall phase begins 1s after the blast
    private static final long FOUNDING_DURATION = SCATTER_TICKS + BlueTntBlock.FUSE_TICKS + POST_EXPLODE_TICKS;

    /** brainNode -> game-time the founding started. Present == founding in progress for that brainNode. */
    private static final Map<GlobalPos, Long> JOBS = new HashMap<>();

    /**
     * Called from driveWalls before a wall plan is created. Returns true to let wall-planning proceed,
     * false to hold it back (founding just started / in progress). When there are no villagers it kicks
     * off the founding and returns false.
     */
    public static boolean gate(MinecraftServer server, ServerLevel level, GlobalPos gp, BrainNodeData data) {
        if (JOBS.containsKey(gp)) return false;                 // founding underway -> not ready
        if (hasVillagersNear(level, gp.pos())) return true;     // a populated village -> normal flow
        startFounding(server, level, gp);
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Map.Entry<GlobalPos, Long>> it = JOBS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlobalPos, Long> e = it.next();
            ServerLevel level = server.getLevel(e.getKey().dimension());
            if (level == null) { it.remove(); continue; }
            if (level.getGameTime() - e.getValue() < FOUNDING_DURATION) continue;
            // founding finished -> mark out the wall now; the settlers become its builders.
            it.remove();
            try {
                BrainNodeEvents.createWallPlanNow(level, e.getKey());
            } catch (Throwable t) {
                TheVillageFightsBack.LOGGER.error("[The Village Fights Back] founding -> wall plan failed", t);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }

    private static boolean hasVillagersNear(ServerLevel level, BlockPos center) {
        return !level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(CHECK_RADIUS),
                Villager::isAlive).isEmpty();
    }

    private static void startFounding(MinecraftServer server, ServerLevel level, GlobalPos gp) {
        JOBS.put(gp, level.getGameTime());
        BlockPos g = gp.pos();

        for (int k = 0; k < SETTLERS; k++) {
            BlockPos spot = findSettlerSpot(level, g, k);
            if (spot == null) {
                continue;
            }
            spawnSettler(level, spot, g);
        }
        spawnGuards(level, g);
    }

    /** Hex point at radius 10 (then radius 3 as a fallback), each searched for a nearby 1x1x2 air bubble. */
    private static BlockPos findSettlerSpot(ServerLevel level, BlockPos brainNode, int k) {
        int[] far = hexOffset(k, HEX_RADIUS);
        BlockPos spot = closestStandable(level, brainNode.offset(far[0], 0, far[1]), BUBBLE_SEARCH);
        if (spot != null) return spot;
        int[] near = hexOffset(k, FALLBACK_RADIUS);
        return closestStandable(level, brainNode.offset(near[0], 0, near[1]), BUBBLE_SEARCH);
    }

    private static int[] hexOffset(int k, double radius) {
        double a = Math.toRadians(k * (360.0 / SETTLERS)); // evenly spaced around the ring (SETTLERS points)
        return new int[]{(int) Math.round(radius * Math.cos(a)), (int) Math.round(radius * Math.sin(a))};
    }

    /** Closest position to {@code around} (within {@code r}) that is a standable 1x1x2 air/cave-air bubble. */
    private static BlockPos closestStandable(ServerLevel level, BlockPos around, int r) {
        BlockPos best = null;
        double bestSqr = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    m.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                    if (!standable(level, m)) continue;
                    double sqr = around.distSqr(m);
                    if (sqr < bestSqr) { bestSqr = sqr; best = m.immutable(); }
                }
        return best;
    }

    /** A 1-wide, 2-tall air (or cave-air) pocket with a solid floor -- room for a villager to stand. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) return false;
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static void spawnSettler(ServerLevel level, BlockPos spot, BlockPos brainNode) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return;
        v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
        v.setBaby(false);
        v.getPersistentData().putBoolean(NBT_FOUNDING, true);
        level.addFreshEntity(v); // onEntityJoin arms it (nerfed) + adds the dormant goals
        v.goalSelector.addGoal(1, new FoundingSettlerGoal(v, brainNode)); // founding takes priority while it runs

        // Drop a jack o'lantern right under its feet (a lit, solid footing) -- unless that block is too
        // tough to break (>= 1200 blast resistance: the brain node / reinforced glass).
        BlockPos floor = spot.below();
        BlockState floorState = level.getBlockState(floor);
        if (floorState.isAir() || floorState.getBlock().getExplosionResistance() < 1200.0F) {
            level.setBlock(floor, Blocks.JACK_O_LANTERN.defaultBlockState(), 3);
        }
    }

    private static void spawnGuards(ServerLevel level, BlockPos brainNode) {
        EntityType<?> guardType = ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID);
        if (guardType == null) return; // Guard Villagers not installed
        int[][] dirs = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int i = 0; i < GUARDS; i++) {
            BlockPos spot = closestStandable(level, brainNode.offset(dirs[i][0], 0, dirs[i][1]), 3);
            if (spot == null) continue;
            Entity g = guardType.create(level);
            if (!(g instanceof Mob mob)) continue;
            mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
            level.addFreshEntity(mob); // onEntityJoin arms the guard
        }
    }
}
