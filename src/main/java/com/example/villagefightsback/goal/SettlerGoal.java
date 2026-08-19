package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.BrainNodeData;
import com.example.villagefightsback.ModBlocks;

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
 * The "settler" behaviour: a villager far from every Village Brain Node -- but in a dimension that HAS at
 * least one -- founds a new home. It drops a Blue TNT on flat ground, lights it, and flees; the TNT then
 * builds a random house with the door facing the fled villager
 * (see {@link BlueTntBlock}/{@link com.example.villagefightsback.HousePlacer}).
 *
 * A villager settles at most once (a persistent NBT flag), so wanderers seed new houses without spamming
 * the world. Cheap to evaluate: the distance/flatness checks only run on a throttle, and a villager near
 * a brainNode (the common case) early-outs immediately.
 */
public class SettlerGoal extends Goal {
    private static final String NBT_SETTLED = "vgun_settled";
    private static final double MIN_DIST_FROM_BRAIN_NODE = 100.0;
    private static final int RETRY_TICKS = 200;     // re-evaluate at most every 10s
    private static final int FLAT_TOLERANCE = 2;     // max ground-height spread under the footprint

    private final Villager villager;
    private int nextTryTick = RETRY_TICKS;
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
        if (com.example.villagefightsback.BuilderRole.isBuilder(villager)) return false; // builders belong to a village
        // Out on a Nightmare hunt: a settler that stops to found a village halfway across the map is not
        // hunting. Lifts itself the moment the hunt ends, so the same villager settles normally afterwards.
        if (com.example.villagefightsback.NightmareRaid.isHunting(villager)) return false;
        if (villager.getPersistentData().getBoolean(NBT_SETTLED)) return false;
        if (villager.tickCount < nextTryTick) return false;
        nextTryTick = villager.tickCount + RETRY_TICKS;

        if (!farFromBrainNodes(level)) return false;
        BlockPos center = villager.blockPosition();
        if (!isFlat(level, center)) return false;

        this.tntPos = center;
        this.doorFacing = villager.getDirection(); // it flees forward -> door ends up facing it
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return false; // the TNT self-detonates on placement -- nothing left to do, no fleeing
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) villager.level();
        // Deliberately still BLUE, not the stage-2 Cyan charge. This settler is not summoned by any node --
        // farFromBrainNodes below is what lets it act, so by construction it is nowhere near one and is seeding
        // a brand-new village. A village starts small whatever the neighbours have grown into.
        BlockState tnt = ModBlocks.BLUE_TNT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, doorFacing);
        level.setBlock(tntPos, tnt, 3); // detonates instantly on placement -> builds the house
        villager.getPersistentData().putBoolean(NBT_SETTLED, true);
    }

    @Override
    public void stop() {
        tntPos = null;
    }

    /**
     * True when this dimension holds at least one Village Brain Node and the villager is far enough from all of
     * them to found a home of its own.
     *
     * <p>The "at least one" half matters: settling is meant to seed a NEW village away from the existing ones,
     * which presupposes there are existing ones. In a dimension with no brain node at all there is nothing to
     * settle away from, so nobody settles -- otherwise every villager in the Nether, the End, Limbo, or an
     * overworld where no node has been founded yet would qualify at once and start dropping houses.
     */
    private boolean farFromBrainNodes(ServerLevel level) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        BlockPos p = villager.blockPosition();
        boolean anyHere = false;
        double minSqr = Double.MAX_VALUE;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(level.dimension())) {
                anyHere = true;
                minSqr = Math.min(minSqr, gp.pos().distSqr(p));
            }
        }
        if (!anyHere) return false;
        return minSqr > MIN_DIST_FROM_BRAIN_NODE * MIN_DIST_FROM_BRAIN_NODE;
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
