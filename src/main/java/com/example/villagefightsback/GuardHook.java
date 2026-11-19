package com.example.villagefightsback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * A guard that has an enemy hanging just out of reach hooks it and drags it in.
 *
 * <p>The village's guards fight at crossbow range, which leaves them with one bad matchup: anything that
 * circles at eight or ten blocks, or that hovers over a river the guard cannot follow into, can plink at the
 * wall forever without ever being caught. The hook closes that gap -- a guard whose target sits between
 * {@value #MIN_RANGE} and {@value #MAX_RANGE} blocks away, in line of sight and hostile to the village, yanks
 * it to arm's length on a {@value #COOLDOWN}-tick cooldown.
 *
 * <p>It is deliberately not a damage source: the pull itself hurts nothing. What it does is turn a standoff
 * into a melee, which is where the village is strong -- and the cooldown lives on the guard's own persistent
 * data, so it survives a reload, needs no per-guard map to keep, and cannot leak when the guard dies.
 */
public final class GuardHook {
    private GuardHook() {}

    private static final String NBT_READY = "vgun_hook_ready";
    private static final int COOLDOWN = 120;       // one hook per guard per 6s
    private static final double MIN_RANGE = 5.0;   // already in reach: nothing to pull
    private static final double MAX_RANGE = 18.0;  // too far to see the point
    private static final double PULL = 0.85;       // blocks/tick toward the guard...
    private static final double LIFT = 0.32;       // ...and up, so the pull clears the ground

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide() || entity.tickCount % 10 != 0) return; // twice a second is plenty
        if (!(entity instanceof Mob guard) || !Factions.isGuard(guard)) return;

        LivingEntity target = guard.getTarget();
        if (target == null || !target.isAlive()) return;
        if (!Factions.isVillageThreat(target)) return;      // never hook one of our own

        double d = guard.distanceToSqr(target);
        if (d < MIN_RANGE * MIN_RANGE || d > MAX_RANGE * MAX_RANGE) return;
        if (!guard.hasLineOfSight(target)) return;
        if (!(guard.level() instanceof ServerLevel level)) return;

        long now = level.getGameTime();
        if (guard.getPersistentData().getLong(NBT_READY) > now) return;
        guard.getPersistentData().putLong(NBT_READY, now + COOLDOWN);

        Vec3 towardGuard = new Vec3(guard.getX() - target.getX(), 0.0, guard.getZ() - target.getZ());
        if (towardGuard.lengthSqr() < 1.0E-4) return;
        target.setDeltaMovement(towardGuard.normalize().scale(PULL).add(0.0, LIFT, 0.0));
        target.hurtMarked = true;

        guard.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, guard.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL, 1.0F, 0.7F);

        // A short line of sparks along the rope, so a pull reads as a pull and not as a mob glitching closer.
        Vec3 from = guard.getEyePosition();
        Vec3 to = target.getEyePosition();
        for (int i = 1; i <= 6; i++) {
            Vec3 p = from.lerp(to, i / 7.0);
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
