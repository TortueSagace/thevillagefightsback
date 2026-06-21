package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.EmeraldMarker;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Rally to the brain's EMERALD MARKER: an IDLE emerald unit runs at the nearest marked target within
 * {@link EmeraldMarker#RALLY_RANGE} blocks (by setting it as its attack target, so its normal melee/flight goals
 * carry it there). Low priority + an explicit "already has a target" guard, so a unit that is already fighting
 * (or retaliating) ignores the marker -- markers only redirect units that have nothing to do.
 */
public class RallyToMarkedGoal extends Goal {
    private final Mob mob;
    private LivingEntity marked;

    public RallyToMarkedGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false; // already fighting -> ignore markers
        marked = EmeraldMarker.nearest(mob, EmeraldMarker.RALLY_RANGE);
        return marked != null;
    }

    @Override
    public boolean canContinueToUse() {
        return marked != null && marked.isAlive() && EmeraldMarker.isMarked(marked)
                && mob.distanceToSqr(marked) <= EmeraldMarker.RALLY_RANGE * EmeraldMarker.RALLY_RANGE;
    }

    @Override
    public void start() {
        mob.setTarget(marked);
    }

    @Override
    public void tick() {
        if (mob.getTarget() != marked) mob.setTarget(marked); // hold the rally target so it keeps pathing in
    }

    @Override
    public void stop() {
        marked = null;
    }
}
