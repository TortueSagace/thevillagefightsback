package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.BrainNodeEvents;
import com.example.villagefightsback.NightmareRaid;

import com.example.villagefightsback.platform.PersistentData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A village unit that has fallen in a hole and stopped getting anywhere is lifted back to its brain node.
 *
 * <p>Villagers walk into pits, into one-block gaps behind a wall course, into the crater a creeper left, and
 * once in they shuffle inside a single block forever. Nothing in the mod noticed: {@code ReturnToVillageGoal}
 * only fires for a villager OUTSIDE its wall, which is exactly the case a pit is not.
 *
 * <p>THE COST IS THE WHOLE DESIGN. This runs on every villager and every guard in the world, so the ordinary
 * case has to be nearly free. It is, because the gates are ordered by price:
 *
 * <ol>
 *   <li>a modulo, which rejects 39 calls in 40;</li>
 *   <li>three int compares against the last sample, which reject everything that is moving;</li>
 *   <li>and only for a unit that has held one block for {@value #STUCK_SAMPLES} samples -- half a minute --
 *       does anything read the world at all.</li>
 * </ol>
 *
 * <p>Nothing below gate 2 runs for a unit that is walking, which is nearly all of them nearly always.
 *
 * <p>It also has to be sure. A villager standing still is usually not stuck: it is asleep, trading, standing
 * at its workstation, or building a wall, and teleporting any of those would be a far worse bug than the one
 * this fixes. So being motionless is necessary and not sufficient -- the unit must ALSO be genuinely boxed
 * in below the surface before it is touched.
 */
public class UnstickVillagerGoal extends Goal {
    /** How often the position is sampled. Matches the brain node's own cadence. */
    private static final int SAMPLE_TICKS = 40;
    /** Consecutive identical samples before a unit in a PIT counts as stuck. 15 x 2s = 30s. */
    private static final int STUCK_SAMPLES = 15;
    /**
     * Consecutive identical samples before a unit counts as stuck FOR ANY REASON. 30 x 2s = 60s.
     *
     * <p>The pit probe was too narrow. Five villagers were found held in place by two opposing water currents:
     * not in a hole, not doing anything, and not going anywhere ever again, and the probe said they were fine.
     * A minute in a single 1x1 spot is stuck whatever is doing it, so after that the cause stops mattering.
     */
    private static final int ANY_SAMPLES = 30;
    /** Both clocks collapse to this when unstick_test_mode is on, so the rescue can be watched. */
    private static final int TEST_SAMPLES = 3;
    /** How far below the surface counts as "in a hole" rather than "indoors". */
    private static final int PIT_DEPTH = 3;
    /** After a failed rescue, wait this long before trying again. The pit is not going anywhere. */
    private static final int RETRY_TICKS = 1200;
    /** How far a unit may be from a brain node and still be the village's problem. Matches INCOME_RADIUS. */
    private static final double NODE_RANGE = 100.0;

    private final PathfinderMob mob;

    private int lastX = Integer.MIN_VALUE;
    private int lastY;
    private int lastZ;
    private int sameSamples;
    private int nextTryTick;
    private BlockPos rescue;

    public UnstickVillagerGoal(PathfinderMob mob) {
        this.mob = mob;
        // No flags: it never competes with combat, building or pathing, and it is over in one tick.
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        // GATE 1 -- one modulo, 39 calls in 40 end here.
        if (mob.tickCount % SAMPLE_TICKS != 0) return false;
        if (mob.tickCount < nextTryTick) return false;

        // GATE 2 -- three int reads and three compares. Everything that is moving ends here.
        int x = mob.getBlockX();
        int y = mob.getBlockY();
        int z = mob.getBlockZ();
        boolean held = x == lastX && z == lastZ && Math.abs(y - lastY) <= 1;
        lastX = x;
        lastY = y;
        lastZ = z;
        if (!held) { sameSamples = 0; return false; }
        ++sameSamples;

        // Two clocks. The pit probe is checked once, on the dot, at the earlier one; past the later one the
        // cause stops mattering. Between the two nothing below runs at all, so a motionless unit still costs
        // three int compares a sample.
        boolean test = com.example.villagefightsback.Config.unstickTestMode();
        int pitAt = test ? TEST_SAMPLES : STUCK_SAMPLES;
        int anyAt = test ? TEST_SAMPLES : ANY_SAMPLES;
        boolean pitTier = sameSamples == pitAt;
        boolean anyTier = sameSamples >= anyAt;
        if (!pitTier && !anyTier) return false;

        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (busyOnPurpose()) { sameSamples = 0; return false; }
        // Below the minute, only a unit that is demonstrably in a hole is touched. Do NOT reset the counter
        // here: not being in a pit is exactly the case that has to be allowed to reach the later clock.
        if (!anyTier && !inAPit(level, x, y, z)) return false;

        GlobalPos node = BrainNodeEvents.nearestNodeTo(level, mob.blockPosition(), NODE_RANGE);
        if (node == null) { sameSamples = 0; return false; }

        rescue = BrainNodeEvents.nearestFit(level, node.pos(), mob.getType(), 5);
        if (rescue == null) {
            // No room by the node either. Do nothing rather than drop it somewhere worse, and stop asking
            // for a while -- a village with no space around its node will not grow one in the next second.
            sameSamples = 0;
            nextTryTick = mob.tickCount + RETRY_TICKS;
            return false;
        }
        return true;
    }

    /** One tick, one teleport, done. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (rescue == null) return;
        if (mob instanceof Villager v && v.isSleeping()) v.stopSleeping();
        mob.getNavigation().stop();          // a stale path would drag it straight back off the edge
        mob.moveTo(rescue.getX() + 0.5, rescue.getY(), rescue.getZ() + 0.5, mob.getYRot(), 0.0F);
        mob.fallDistance = 0.0F;
        com.example.villagefightsback.TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] {} had not moved for {}s; the brain node pulled it back to {}.",
                mob.getType().getDescription().getString(),
                (sameSamples * SAMPLE_TICKS) / 20, rescue);
        sameSamples = 0;
        nextTryTick = mob.tickCount + RETRY_TICKS;
        rescue = null;
    }

    /**
     * Is this unit standing still because it is doing its job?
     *
     * <p>Every one of these is a village unit that is legitimately motionless, and each was a teleport bug
     * waiting to happen. A villager at its workstation does not move BY DEFINITION.
     */
    private boolean busyOnPurpose() {
        if (mob.isPassenger() || mob.isLeashed()) return true;     // a boat is not a pit
        if (NightmareRaid.isHunting(mob)) return true;             // out on a raid, and where it means to be
        if (!(mob instanceof Villager v)) return false;            // a guard has none of the roles below
        if (v.isBaby() || v.isSleeping() || v.isTrading()) return true;
        // At its workstation. A working villager is motionless BY DEFINITION, and with the pit requirement
        // gone this is the check that stops one being teleported off its job every single minute. It was safe
        // to leave out while a rescue also demanded a hole; it is not safe now.
        if (v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                .map(site -> site.pos().distSqr(v.blockPosition()) <= 9.0).orElse(false)) return true;
        if (com.example.villagefightsback.BuilderRole.isBuilder(v)) return true;
        if (com.example.villagefightsback.infection.MinerRole.isMiner(v)) return true;
        // A settler is walking out to found something and has its own give-up clock.
        return PersistentData.of(v).getBoolean(com.example.villagefightsback.ExpansionManager.NBT_EXPANSION)
                || PersistentData.of(v).getBoolean(com.example.villagefightsback.FoundingManager.NBT_FOUNDING);
    }

    /**
     * Genuinely boxed in below the surface, rather than merely indoors.
     *
     * <p>Being under a roof is normal village life; being three blocks below the open sky with walls on every
     * side is a hole. One heightmap read and four block reads, paid only by a unit that has already been
     * motionless for half a minute.
     */
    private boolean inAPit(ServerLevel level, int x, int y, int z) {
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - y < PIT_DEPTH) return false;
        BlockPos head = new BlockPos(x, y + 1, z);
        return level.getBlockState(head.east()).blocksMotion()
                && level.getBlockState(head.west()).blocksMotion()
                && level.getBlockState(head.north()).blocksMotion()
                && level.getBlockState(head.south()).blocksMotion();
    }
}
