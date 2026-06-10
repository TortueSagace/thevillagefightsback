package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.BlueTntBlock;
import com.example.villagergunners.GravemindData;
import com.example.villagergunners.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The "settler" behaviour: a villager far from every Village Gravemind founds a new home. It drops a
 * Blue TNT on flat ground, lights it, and flees; the TNT then builds a random house with the door
 * facing the fled villager (see {@link BlueTntBlock}/{@link com.example.villagergunners.HousePlacer}).
 *
 * A villager settles at most once (a persistent NBT flag), so wanderers seed new houses without spamming
 * the world. Cheap to evaluate: the distance/flatness checks only run on a throttle, and a villager near
 * a gravemind (the common case) early-outs immediately.
 */
public class SettlerGoal extends Goal {
    private static final String NBT_SETTLED = "vgun_settled";
    private static final double MIN_DIST_FROM_GRAVEMIND = 100.0;
    private static final int RETRY_TICKS = 200;     // re-evaluate at most every 10s
    private static final int FLEE_DIST = 7;          // clear of the 7x7 footprint
    private static final int FLAT_TOLERANCE = 2;     // max ground-height spread under the footprint

    private final Villager villager;
    private int nextTryTick = RETRY_TICKS;
    private int timer;
    private BlockPos tntPos;
    private Direction doorFacing = Direction.NORTH;

    public SettlerGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        if (villager.isBaby() || villager.isSleeping()) return false;
        if (villager.getPersistentData().getBoolean(NBT_SETTLED)) return false;
        if (villager.tickCount < nextTryTick) return false;
        nextTryTick = villager.tickCount + RETRY_TICKS;

        if (!farFromGraveminds(level)) return false;
        BlockPos center = villager.blockPosition();
        if (!isFlat(level, center)) return false;

        this.tntPos = center;
        this.doorFacing = villager.getDirection(); // it flees forward -> door ends up facing it
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return tntPos != null && timer > 0;
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) villager.level();
        BlockState tnt = ModBlocks.BLUE_TNT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, doorFacing);
        level.setBlock(tntPos, tnt, 3);
        ((BlueTntBlock) ModBlocks.BLUE_TNT.get()).prime(level, tntPos);
        villager.getPersistentData().putBoolean(NBT_SETTLED, true);
        timer = BlueTntBlock.FUSE_TICKS + 40; // flee a little past the fuse
        fleeForward();
    }

    @Override
    public void tick() {
        timer--;
        if (villager.getNavigation().isDone()) fleeForward();
    }

    @Override
    public void stop() {
        tntPos = null;
        timer = 0;
    }

    private void fleeForward() {
        BlockPos t = villager.blockPosition().relative(doorFacing, FLEE_DIST);
        villager.getNavigation().moveTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, 1.3);
    }

    private boolean farFromGraveminds(ServerLevel level) {
        GravemindData data = GravemindData.get(level.getServer());
        BlockPos p = villager.blockPosition();
        double minSqr = Double.MAX_VALUE;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(level.dimension())) {
                minSqr = Math.min(minSqr, gp.pos().distSqr(p));
            }
        }
        return minSqr > MIN_DIST_FROM_GRAVEMIND * MIN_DIST_FROM_GRAVEMIND;
    }

    /** The 7x7 footprint must rest on roughly level ground at the villager's feet. */
    private boolean isFlat(ServerLevel level, BlockPos center) {
        int feet = center.getY();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int[][] pts = {{0, 0}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int[] d : pts) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + d[0], center.getZ() + d[1]);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        // surface heights close together AND close to where the villager stands (feet level)
        return (max - min) <= FLAT_TOLERANCE && Math.abs(min - feet) <= FLAT_TOLERANCE;
    }
}
