package com.example.villagefightsback;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Pathfinding throttle. Profiling showed mob pathfinding (the A* in PathFinder/NodeEvaluator, plus the block
 * lookups it drives) is by far the biggest server cost, and our combat/settler/wall goals were re-issuing
 * {@code getNavigation().moveTo(...)} EVERY tick -- each call recomputes a full path. Routing those calls
 * through here caps a mob to ~one path recompute per {@value #REPATH_TICKS} ticks (it keeps following the
 * existing path in between), recomputes early only if the destination jumps more than ~2 blocks, and stops
 * pathing entirely once the mob is right next to its target. Behaviour is unchanged; the cost drops ~10x.
 */
public final class NavThrottle {
    private NavThrottle() {}

    private static final int REPATH_TICKS = 10;          // recompute a path at most ~twice a second
    private static final double CLOSE_SQR = 4.0;         // within ~2 blocks of a chased entity: stop pathing
    private static final double GOAL_CLOSE_SQR = 2.25;   // within ~1.5 blocks of a fixed spot: stop pathing
    private static final double TARGET_MOVED_SQR = 4.0;  // recompute early if the destination jumped >2 blocks

    private static final Map<Mob, long[]> LAST = new WeakHashMap<>(); // mob -> [lastRepathGameTime, lastDestPacked]

    /** Chase a moving entity (e.g. a combat target), throttled. Stops pathing once adjacent. */
    public static void follow(Mob mob, LivingEntity target, double speed) {
        if (mob.distanceToSqr(target) <= CLOSE_SQR) { stopIfMoving(mob); return; }
        if (shouldRepath(mob, target.blockPosition())) mob.getNavigation().moveTo(target, speed);
    }

    /** Walk to a fixed spot (e.g. a build/settle position), throttled. Stops pathing once basically there. */
    public static void goTo(Mob mob, double x, double y, double z, double speed) {
        if (mob.distanceToSqr(x, y, z) <= GOAL_CLOSE_SQR) { stopIfMoving(mob); return; }
        if (shouldRepath(mob, new BlockPos(x, y, z))) mob.getNavigation().moveTo(x, y, z, speed);
    }

    private static void stopIfMoving(Mob mob) {
        if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
    }

    private static boolean shouldRepath(Mob mob, BlockPos dest) {
        long gt = mob.level.getGameTime();
        long[] l = LAST.get(mob);
        if (l == null) {
            LAST.put(mob, new long[]{gt, dest.asLong()});
            return true;
        }
        if (gt - l[0] >= REPATH_TICKS || BlockPos.of(l[1]).distSqr(dest) > TARGET_MOVED_SQR) {
            l[0] = gt;
            l[1] = dest.asLong();
            return true;
        }
        return false;
    }
}
