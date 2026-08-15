package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.NightmareRaid;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A raider walks to where its quarry IS, whether or not it can see them.
 *
 * <p>Every other combat goal in this mod needs line of sight, and rightly: a villager should not swing at
 * something it cannot see. But that made the hunt fall apart the moment the player broke line of sight -- the
 * raiders simply stopped, because nothing was telling them where to go. A hunt is exactly the case where the
 * village does know: the scent is the conceit, and the scent does not care about walls.
 *
 * <p>It also STRIKES, which it did not at first, and the first version was wrong. The village's ordinary combat
 * goals each have their own conditions -- range, sight, stage, whether the villager is a builder, whether the
 * target is past its wall -- and a raider on a hunt satisfies some and not others, so it arrived, swung the
 * bare-handed animation every Mob does by default, and did nothing. Rather than find and loosen each of those
 * conditions in turn, the hunt carries the one thing it must guarantee: a raider that reaches you hurts you.
 *
 * <p>It walks to a WAYPOINT rather than to the player. Minecraft's pathfinder gives up well before a thousand
 * blocks, so asking for the player's exact position from that far away returns no path at all and the raider
 * stands still. Re-aiming at a point a short way along the line, over and over, crosses any distance. Where
 * even that fails -- a wall, a sealed room -- the pathfind failure is precisely what wakes the digging goal.
 */
public class NightmareHuntGoal extends Goal {
    /** How far ahead to aim. Comfortably inside the pathfinder's range, so it always returns a real path. */
    private static final double WAYPOINT = 16.0;
    private static final double SPEED = 1.15;
    /** Re-aim a few times a second: often enough to follow a running player, rarely enough to be cheap. */
    private static final int REPATH_TICKS = 10;

    /** Reach, squared. About the same as a vanilla mob's melee. */
    private static final double REACH_SQR = 4.0;
    /** Damage per blow. In line with the stone axe the village arms its villagers with. */
    private static final float DAMAGE = 5.0F;
    /** Ticks between blows, so a raid is a fight and not a shredder. */
    private static final int SWING_TICKS = 20;

    private int swingCooldown;

    private final PathfinderMob mob;
    private int cooldown;

    public NightmareHuntGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return NightmareRaid.isHunting(mob) && NightmareRaid.huntedBy(mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        Player quarry0 = NightmareRaid.huntedBy(mob);
        if (quarry0 != null && --swingCooldown <= 0 && mob.distanceToSqr(quarry0) <= REACH_SQR) {
            swingCooldown = SWING_TICKS;
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            quarry0.hurt(mob.damageSources().mobAttack(mob), DAMAGE);
        }

        if (--cooldown > 0) return;
        cooldown = REPATH_TICKS;

        Player quarry = NightmareRaid.huntedBy(mob);
        if (quarry == null) return;
        // Only lead the raider while it cannot see its quarry. Once it can, the combat goals own the approach,
        // and two goals steering the same legs is how a mob ends up vibrating between two destinations.
        if (mob.getSensing().hasLineOfSight(quarry)) return;

        Vec3 to = quarry.position().subtract(mob.position());
        double dist = to.length();
        Vec3 aim = dist <= WAYPOINT ? quarry.position()
                : mob.position().add(to.scale(WAYPOINT / dist));
        mob.getNavigation().moveTo(aim.x, aim.y, aim.z, SPEED);
    }
}
