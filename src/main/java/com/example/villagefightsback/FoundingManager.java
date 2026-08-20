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
    static double CHECK_RADIUS = 64.0;  // brain influence radius for founding (set from Config.apply(); base 64)
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
        // A node a Village Spirit woke inside a village that was ALREADY standing never founds anything. The
        // houses, the paths and the wall footprint are all there; seven settlers lighting Blue TNT would
        // build a second village through the first. It gets the same headcount as plain villagers and the
        // same four guards, and wall planning proceeds immediately rather than waiting on a founding job.
        if (data.isRevived(gp)) {
            repopulate(level, gp);
            return true;
        }
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

        // multiplier_number_of_settlers scales the count; the ring divides by the actual count so they stay
        // evenly spaced around it (distinct spots, never stacked) -- the scatter goal then separates them further.
        int count = Math.max(1, (int) Math.round(SETTLERS * Config.settlerCountMult()));
        for (int k = 0; k < count; k++) {
            BlockPos spot = findSettlerSpot(level, g, k, count);
            if (spot == null) {
                continue;
            }
            spawnSettler(level, spot, g);
        }
        spawnGuards(level, g);
    }

    /** Hex point at radius 10 (then radius 3 as a fallback), each searched for a nearby 1x1x2 air bubble. */
    private static BlockPos findSettlerSpot(ServerLevel level, BlockPos brainNode, int k, int total) {
        int[] far = hexOffset(k, total, HEX_RADIUS);
        BlockPos spot = closestStandable(level, brainNode.offset(far[0], 0, far[1]), BUBBLE_SEARCH);
        if (spot != null) return spot;
        int[] near = hexOffset(k, total, FALLBACK_RADIUS);
        return closestStandable(level, brainNode.offset(near[0], 0, near[1]), BUBBLE_SEARCH);
    }

    private static int[] hexOffset(int k, int total, double radius) {
        double a = Math.toRadians(k * (360.0 / total)); // evenly spaced around the ring (total points)
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

    /**
     * A 1-wide, 2-tall pocket a villager can stand in, with a solid floor.
     *
     * <p>Tested by COLLISION, not by air. Asking for air meant a snow layer disqualified the cell, and in a
     * snowy biome every surface cell has one, so the whole outdoors read as unstandable: the hex ring found
     * nothing, the fallback ring found nothing, and the few spots that did pass were the snow-free patches
     * around the village centre and its paths. All four settlers were then dropped in that same huddle, and
     * one second of scattering could not undo it, so their houses went up on top of each other. The same
     * carpet-shaped hole would swallow tall grass and flowers on a plains start.
     *
     * <p>{@link ExpansionManager} already tests it this way, which is why village EXPANSION spaced its
     * buildings correctly in the same world that founding botched. This now matches it, fluid check included,
     * so a settler is never dropped standing in water either.
     */
    private static boolean standable(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return false;
        if (!level.getBlockState(p).getFluidState().isEmpty()) return false; // not stood in water
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /**
     * Fill a revived village with people instead of founders.
     *
     * <p>The same ring, the same count and the same guards as {@link #startFounding} -- what is missing is
     * everything that makes a settler a settler: no {@link FoundingSettlerGoal}, no Blue TNT, no founding
     * flag, and no jack o'lantern under anybody's feet. A standing village does not want seven house-shaped
     * explosions in it, and it does not want lanterns scattered through its streets either.
     */
    private static void repopulate(ServerLevel level, GlobalPos gp) {
        BlockPos g = gp.pos();
        int count = Math.max(1, (int) Math.round(SETTLERS * Config.settlerCountMult()));
        int placed = 0;
        for (int k = 0; k < count; k++) {
            BlockPos spot = findSettlerSpot(level, g, k, count);
            if (spot == null) continue;
            spawnVillager(level, spot);
            placed++;
        }
        spawnGuards(level, g);
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] the woken brain node at {} took on {} villager(s) and its guards "
                + "instead of founding.", g, placed);
    }

    /** A plain villager: none of a settler's freight, and nothing to undo once it lands. */
    private static void spawnVillager(ServerLevel level, BlockPos spot) {
        Villager v = com.example.villagefightsback.compat.MCACompat.createVillager(level);
        if (v == null) return;
        v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
        v.setBaby(false);
        level.addFreshEntity(v);   // onEntityJoin arms it (nerfed) and adds the dormant goals
    }

    private static void spawnSettler(ServerLevel level, BlockPos spot, BlockPos brainNode) {
        // An MCA human when MCA Reborn is installed, else a vanilla villager (see MCACompat).
        Villager v = com.example.villagefightsback.compat.MCACompat.createVillager(level);
        if (v == null) return;
        v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
        v.setBaby(false);
        v.getPersistentData().putBoolean(NBT_FOUNDING, true);
        com.example.villagefightsback.compat.VillagerRoleTags.mirror(v); // survive an MCA replacement we did not cause
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
        // An MCA human wearing the mca:guard profession when MCA is installed, else the Guard Villagers guard.
        EntityType<?> guardType = com.example.villagefightsback.compat.MCACompat.guardType(level.getRandom());
        if (guardType == null) return; // neither MCA nor Guard Villagers installed
        int[][] dirs = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int i = 0; i < GUARDS; i++) {
            BlockPos spot = closestStandable(level, brainNode.offset(dirs[i][0], 0, dirs[i][1]), 3);
            if (spot == null) continue;
            Entity g = guardType.create(level);
            if (!(g instanceof Mob mob)) continue;
            mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
            com.example.villagefightsback.compat.MCACompat.makeGuard(mob); // pin the guard profession BEFORE the join handler reads it
            level.addFreshEntity(mob); // onEntityJoin arms the guard
        }
    }
}
