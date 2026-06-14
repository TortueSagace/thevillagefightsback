package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.ExpansionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

/**
 * A stage-3 EXPANSION settler: walk OUTWARD past the wall along an assigned bearing, find a spot whose
 * building footprint clears the wall and every other settler's claim, and -- only after a per-settler
 * stagger delay (so settlers don't all detonate at once and overlap) -- drop a Blue/Green TNT to found a
 * building. Then revert to an ordinary villager. If no safe spot turns up after a while, it gives up
 * rather than place somewhere it would wreck the wall. Spawned/marked by {@link ExpansionManager}.
 */
public class ExpansionSettlerGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final double REACH_SQR = 6.25;   // ~2.5 blocks from the target column
    private static final int REPATH = 20;
    private static final int MAX_SEEK = 300;        // ticks of seeking past the delay before giving up
    private static final double BUILD_RING = 11.0;  // target build centre this far beyond the wall radius
    private static final double WALL_CLEAR = 9.0;   // build centre must sit at least this far past the wall
    private static final double MIN_SPACING = 16.0; // keep this far from other settlers' claimed builds
    private static final int SURFACE_SEARCH = 4;

    private final Villager villager;
    private final BlockPos gravemind;
    private final double angle;
    private final double wallRadius;
    private final int tntColor;
    private final int placeDelay;

    private int startTick;
    private double extraOut;
    private int repathTimer;
    private boolean done;

    public ExpansionSettlerGoal(Villager villager, BlockPos gravemind,
                                double angle, double wallRadius, int tntColor, int placeDelay) {
        this.villager = villager;
        this.gravemind = gravemind;
        this.angle = angle;
        this.wallRadius = wallRadius;
        this.tntColor = tntColor;
        this.placeDelay = placeDelay;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !done
                && villager.level() instanceof ServerLevel
                && villager.getPersistentData().getBoolean(ExpansionManager.NBT_EXPANSION);
    }

    @Override
    public boolean canContinueToUse() {
        return !done && villager.getPersistentData().getBoolean(ExpansionManager.NBT_EXPANSION);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        startTick = villager.tickCount;
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) villager.level();
        int elapsed = villager.tickCount - startTick;
        BlockPos target = columnAt(wallRadius + BUILD_RING + extraOut);

        if (--repathTimer <= 0) {
            villager.getNavigation().moveTo(target.getX() + 0.5, villager.getY(), target.getZ() + 0.5, SPEED);
            repathTimer = REPATH;
        }

        boolean near = horizSqr(villager.blockPosition(), target) < REACH_SQR || villager.getNavigation().isDone();
        if (elapsed < placeDelay || !near) return; // still travelling, or waiting out the stagger delay

        BlockPos spot = ExpansionManager.surfaceStandable(level, target, SURFACE_SEARCH);
        if (spot != null && isValid(level, spot)) {
            ExpansionManager.placeTnt(level, spot, tntColor, gravemind);
            ExpansionManager.claim(level, spot);
            finish();
            return;
        }
        // spot taken / too close to the wall -> push the target further out and keep looking, or give up.
        extraOut += 4.0;
        if (elapsed > placeDelay + MAX_SEEK) finish();
    }

    /** A spot is good if its footprint clears the wall and it's far enough from every claimed build. */
    private boolean isValid(ServerLevel level, BlockPos spot) {
        double dx = spot.getX() - gravemind.getX();
        double dz = spot.getZ() - gravemind.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return dist >= wallRadius + WALL_CLEAR && ExpansionManager.isClear(level, spot, MIN_SPACING);
    }

    private void finish() {
        villager.getPersistentData().remove(ExpansionManager.NBT_EXPANSION); // back to an ordinary villager
        villager.getNavigation().stop();
        done = true;
    }

    private BlockPos columnAt(double radius) {
        return new BlockPos(
                gravemind.getX() + (int) Math.round(Math.cos(angle) * radius),
                gravemind.getY(),
                gravemind.getZ() + (int) Math.round(Math.sin(angle) * radius));
    }

    private static double horizSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
