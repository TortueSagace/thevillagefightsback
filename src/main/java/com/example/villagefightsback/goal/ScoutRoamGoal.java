package com.example.villagefightsback.goal;

import com.example.villagefightsback.BrainNodeData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * A SCOUT's roam: instead of milling around its spawn like an ordinary stroll, an emerald scout (cat/bat/mite)
 * heads OUTWARD -- it biases each wander target away from the nearest Village Brain Node, so it leaves the
 * village to go hunting for prey to infect. Falls back to the normal water-avoiding stroll when no brain node
 * is known or the outward target isn't reachable.
 */
public class ScoutRoamGoal extends WaterAvoidingRandomStrollGoal {
    private static final int ROAM_RADIUS = 18; // pick targets far out so the scout actually travels

    public ScoutRoamGoal(PathfinderMob mob, double speed) {
        super(mob, speed);
    }

    @Override
    protected Vec3 getPosition() {
        Vec3 away = outwardDir();
        if (away != null) {
            Vec3 toward = this.mob.position().add(away.scale(ROAM_RADIUS));
            Vec3 pos = LandRandomPos.getPosTowards(this.mob, ROAM_RADIUS, 7, toward);
            if (pos != null) return pos;
        }
        return super.getPosition(); // no node / no reachable outward spot -> ordinary water-avoiding stroll
    }

    /** Horizontal unit vector pointing AWAY from the nearest brain node in this dimension (null if none or on it). */
    private Vec3 outwardDir() {
        if (!(this.mob.level() instanceof ServerLevel sl)) return null;
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (GlobalPos gp : BrainNodeData.get(sl.getServer()).blocks()) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            double d = gp.pos().distSqr(this.mob.blockPosition());
            if (d < best) { best = d; nearest = gp.pos(); }
        }
        if (nearest == null) return null;
        Vec3 dir = new Vec3(this.mob.getX() - (nearest.getX() + 0.5), 0.0, this.mob.getZ() - (nearest.getZ() + 0.5));
        return dir.lengthSqr() < 1.0 ? null : dir.normalize();
    }
}
