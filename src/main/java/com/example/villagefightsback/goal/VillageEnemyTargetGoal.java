package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.VillageEvents;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Take the village's current grievance as a target: whoever {@link VillageEvents#getAggroPlayer} names.
 *
 * <p>That method already knows the whole answer -- the player who just struck this unit, or any Enemy of the
 * Village standing near the node -- and the villagers' and wall defenders' combat goals have always consulted
 * it. IRON GOLEMS never did. They only ever turned on a player when something explicitly set their target, so
 * an Enemy of the Village could walk through a village of golems untouched, which is precisely backwards: the
 * golem is the piece of the village most obviously built to answer for it.
 *
 * <p>A target goal rather than a poll, so it competes with the golem's other targeting on equal terms instead
 * of overriding whatever it was already doing.
 */
public class VillageEnemyTargetGoal extends Goal {
    private final Mob mob;

    public VillageEnemyTargetGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return VillageEvents.getAggroPlayer(mob) != null;
    }

    @Override
    public void start() {
        mob.setTarget(VillageEvents.getAggroPlayer(mob));
    }

    /** Hold the target only while the village still has the grievance; getAggroPlayer expiring ends it. */
    @Override
    public boolean canContinueToUse() {
        LivingEntity current = mob.getTarget();
        return current instanceof Player && current.isAlive()
                && VillageEvents.getAggroPlayer(mob) == current;
    }

    @Override
    public void stop() {
        mob.setTarget(null);
    }
}
