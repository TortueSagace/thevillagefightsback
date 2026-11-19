package com.example.villagefightsback;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Guards swim. Water stops being the hole in the village's defence.
 *
 * <p>The village's fighting strength is the guard (Guard Villagers' {@code guardvillagers:guard}), and a
 * guard is a villager with a crossbow: it will not path into water, it sinks, and it drowns. An attacker
 * standing in a river, or a raid that crosses one, simply waits the village out -- the wall is irrelevant
 * because the water is already a wall.
 *
 * <p>Three small pieces, all on the vanilla mob, since Guard Villagers is a runtime soft dependency and
 * there is nothing in it to compile against:
 * <ul>
 *   <li>the join handler gives a guard a zero pathfinding cost for water and water edges, so its own AI
 *       routes a chase straight through a river instead of pacing the bank;</li>
 *   <li>while it is in water it keeps its air (no drowning), stays at the surface instead of sinking to the
 *       bed, and swims toward its target;</li>
 *   <li>and as a backstop, drowning damage to a guard is cancelled outright, so a guard that ends up under
 *       an overhang or is shoved down by a fight cannot be killed by the water either.</li>
 * </ul>
 */
public final class GuardSwimming {
    private GuardSwimming() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob) || !Factions.isGuard(mob)) return;
        mob.setPathfindingMalus(PathType.WATER, 0.0F);
        mob.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Mob guard) || !Factions.isGuard(guard)) return;
        if (!guard.isInWater()) return;

        guard.setAirSupply(guard.getMaxAirSupply()); // water is not their weakness any more
        guard.setSwimming(true);

        Vec3 motion = guard.getDeltaMovement();
        // Float, don't sink: a guard that reaches the bed just stands there until it drowns.
        if (motion.y < 0.02) guard.setDeltaMovement(motion.x, 0.02, motion.z);

        LivingEntity target = guard.getTarget();
        if (target == null || !target.isAlive()) return;
        Vec3 toward = new Vec3(target.getX() - guard.getX(), 0.0, target.getZ() - guard.getZ());
        if (toward.lengthSqr() < 1.0) return;
        Vec3 push = toward.normalize().scale(0.02);
        guard.setDeltaMovement(guard.getDeltaMovement().add(push.x, 0.0, push.z));
    }

    @SubscribeEvent
    public static void onDrowning(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_DROWNING)) return;
        if (Factions.isGuard(event.getEntity())) event.setCanceled(true);
    }
}
