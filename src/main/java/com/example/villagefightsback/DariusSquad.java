package com.example.villagefightsback;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * General Darius's hired guard squad. Darius "hires" any nearby village guard (a Guard Villagers guard or a
 * {@link VindictiveGuardEntity}) that is not currently fighting. Hired guards form a squad that follows Darius
 * around when idle and rallies back to him after each fight; guards still attack any enemy on sight. The squad
 * is a transient set of UUIDs owned by one Darius -- it is released (cleared) when Darius dies or despawns.
 *
 * This is deliberately lightweight: rather than injecting goals into the guardvillagers AI (a runtime soft-dep
 * whose goal set we do not control), each tick we simply (a) look for new hireable guards near Darius, and
 * (b) for every hired guard that currently has NO combat target, nudge its navigation toward Darius. Guards
 * keep their own target-acquisition + combat goals untouched, so they break off to fight on their own and then
 * (having no target again) drift back toward Darius. See {@link GeneralDariusEntity} for the per-tick driver.
 */
public final class DariusSquad {
    private DariusSquad() {}

    /** The Guard Villagers guard entity type id (runtime soft-dep). */
    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");

    public static final double HIRE_RADIUS = 5.0;     // hire guards within this many blocks
    private static final double FOLLOW_RADIUS = 64.0;  // keep / command hired guards within this range
    private static final double FOLLOW_STOP_DIST = 4.0; // stop nudging once this close to Darius
    private static final double FOLLOW_SPEED = 1.1;

    /** True if {@code e} is a village guard Darius can hire (a guardvillagers guard or a vindictive guard). */
    public static boolean isHireableGuard(Entity e) {
        if (e instanceof VindictiveGuardEntity) return true;
        return GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    /** A guard is "fighting" if it currently has a live attack target. */
    private static boolean isFighting(Mob m) {
        LivingEntity t = m.getTarget();
        return t != null && t.isAlive();
    }

    /**
     * Per-tick squad upkeep for one Darius. Recruits new idle guards within {@link #HIRE_RADIUS}, prunes dead /
     * gone / far members, and -- for every hired guard NOT currently fighting -- nudges it to follow Darius.
     */
    public static void tick(ServerLevel level, GeneralDariusEntity darius, Set<UUID> squad) {
        // 1) Recruit nearby idle guards.
        for (Mob m : level.getEntitiesOfClass(Mob.class, darius.getBoundingBox().inflate(HIRE_RADIUS),
                e -> e != darius && e.isAlive() && isHireableGuard(e) && !isFighting(e))) {
            if (squad.add(m.getUUID())) {
                level.playSound(null, m.getX(), m.getY(), m.getZ(),
                        net.minecraft.sounds.SoundEvents.VILLAGER_YES,
                        net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 0.8F);
            }
        }

        // 2) Command / prune the squad.
        Iterator<UUID> it = squad.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive() || m.distanceTo(darius) > FOLLOW_RADIUS) {
                it.remove();
                continue;
            }
            // A hired guard that is NOT fighting follows Darius (rallies back to him after a fight).
            if (!isFighting(m) && m instanceof PathfinderMob pm) {
                if (pm.distanceTo(darius) > FOLLOW_STOP_DIST) {
                    NavThrottle.follow(pm, darius, FOLLOW_SPEED);
                } else {
                    pm.getNavigation().stop();
                    pm.getLookControl().setLookAt(darius, 30.0F, 30.0F);
                }
            }
        }
    }

    /** Release every hired guard (called when Darius dies or despawns): they simply stop following. */
    public static void release(ServerLevel level, Set<UUID> squad) {
        for (UUID id : squad) {
            if (level.getEntity(id) instanceof PathfinderMob pm) pm.getNavigation().stop();
        }
        squad.clear();
    }

    /** Count of currently-tracked squad members (for boss-bar / debug use). */
    public static int size(Set<UUID> squad) {
        return squad.size();
    }
}
