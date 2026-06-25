package com.example.villagefightsback.goal;

import javax.annotation.Nullable;

import com.example.villagefightsback.BrainNodeData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.phys.Vec3;

/**
 * The aquatic counterpart of {@link ScoutRoamGoal}: an emerald WATER scout (the emerald salmon) roams LARGE water
 * zones hunting for prey instead of milling around its spawn. Each wander target is the swimmable spot -- sampled
 * over a wide radius -- that lies FARTHEST from the nearest Village Brain Node, so the school pushes OUTWARD across
 * rivers/lakes/oceans, away from the village core, to find creatures to infect. Falls back to the ordinary wide
 * random swim when no brain node is known or no swimmable spot turns up.
 */
public class AquaticScoutRoamGoal extends RandomSwimmingGoal {
    private static final int H_RANGE = 24; // sample swim targets this far out horizontally (a wide water sweep)
    private static final int V_RANGE = 8;  // ...and this far through the water column (dive / rise)
    private static final int SAMPLES = 5;  // candidate swim targets per pick; keep the most OUTWARD one

    public AquaticScoutRoamGoal(PathfinderMob mob, double speed, int interval) {
        super(mob, speed, interval);
    }

    @Override
    @Nullable
    protected Vec3 getPosition() {
        Vec3 node = nearestNode();
        Vec3 best = null;
        double bestScore = -1.0;
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 c = BehaviorUtils.getRandomSwimmablePos(this.mob, H_RANGE, V_RANGE);
            if (c == null) continue;
            // No node -> just reach as far from HERE as possible; otherwise bias toward the spot farthest from it.
            double score = node == null ? this.mob.position().distanceToSqr(c) : c.distanceToSqr(node);
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best != null ? best : super.getPosition();
    }

    /** Centre of the nearest Village Brain Node in this dimension (null if none). */
    @Nullable
    private Vec3 nearestNode() {
        if (!(this.mob.level instanceof ServerLevel sl)) return null;
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (GlobalPos gp : BrainNodeData.get(sl.getServer()).blocks()) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            double d = gp.pos().distSqr(this.mob.blockPosition());
            if (d < best) { best = d; nearest = gp.pos(); }
        }
        return nearest == null ? null : Vec3.atCenterOf(nearest);
    }
}
