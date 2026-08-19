package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.BrainNodeData;
import com.example.villagefightsback.NavThrottle;
import com.example.villagefightsback.wall.WallBuild;
import com.example.villagefightsback.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

/**
 * Brings a stray village villager home. Villagers must NEVER cross their wall to chase a mob (the combat goals
 * enforce that via {@link com.example.villagefightsback.VillageEvents#beyondHomeWall}), but one that ends up
 * OUTSIDE the wall -- spawned out, knocked out, or having chased a mob while already outside -- should head
 * back toward its village brain node (i.e. back inside the wall) once it is not fighting.
 *
 * Low priority + the MOVE flag: a combat goal (priority 1-2) always preempts it, so a villager finishes any
 * fight first and only walks home when idle. Only triggers for a villager near a WALLED village, so wild
 * villagers and far-off settlers are unaffected.
 */
public class ReturnToVillageGoal extends Goal {
    private static final double SEARCH = 80.0;   // only return to a walled village within this
    private static final int RECHECK = 20;       // re-evaluate at most once per second (cheap)

    private final Villager villager;
    private BlockPos home;
    private boolean outside = true;   // last enclosingPlan answer; see canContinueToUse

    public ReturnToVillageGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (villager.tickCount % RECHECK != 0) return false;
        if (!(villager.level() instanceof ServerLevel sl)) return false;
        if (WallBuild.enclosingPlan(sl, villager.blockPosition()) != null) return false; // already inside a wall
        this.outside = true;
        this.home = nearestWalledNode(sl);
        return home != null;
    }

    /**
     * Still outside? Re-tested on the same throttle {@link #canUse} uses, and for the same reason.
     *
     * <p>{@code enclosingPlan} runs a point-in-polygon test against every column of every unfinished wall, and a
     * wall runs to a thousand columns. Asking that every tick, for every villager walking home, is a real cost
     * for an answer that cannot change meaningfully between ticks -- the villager is walking. Arriving inside
     * the wall up to {@value #RECHECK} ticks before the goal notices costs nothing: it is already heading there.
     */
    @Override
    public boolean canContinueToUse() {
        if (home == null || !(villager.level() instanceof ServerLevel sl)) return false;
        if (villager.tickCount % RECHECK != 0) return outside;
        outside = WallBuild.enclosingPlan(sl, villager.blockPosition()) == null;
        return outside;
    }

    @Override
    public void stop() {
        home = null;
        villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (home != null) {
            villager.getLookControl().setLookAt(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            NavThrottle.goTo(villager, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.9);
        }
    }

    /** The brain node of the nearest WALLED village within {@link #SEARCH} blocks, or null. */
    private BlockPos nearestWalledNode(ServerLevel sl) {
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        BlockPos best = null;
        double bestD = SEARCH * SEARCH;
        for (WallPlan plan : data.wallPlans()) {
            if (!plan.brainNode().dimension().equals(sl.dimension())) continue;
            BlockPos node = plan.brainNode().pos();
            double d = node.distSqr(villager.blockPosition());
            if (d < bestD) { bestD = d; best = node; }
        }
        return best;
    }
}
