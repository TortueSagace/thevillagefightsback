package com.example.villagefightsback;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Limbo-gear banish beam: a player-fired clone of the Unleashed BrainNode's green sonic-boom wave. It
 * sweeps a 100-block line from the wearer's eyes and banishes every hostile (non-Limbo) creature it touches to
 * Limbo. Granted by wearing the full Limbo armour set ({@link LimboAbility}).
 */
public final class LimboBeam {
    private LimboBeam() {}

    private static final double RANGE = 100.0;
    private static final double RADIUS = 5.0;

    public static void fire(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        Vec3 origin = player.getEyePosition();
        Vec3 dir = player.getLookAngle();

        // green sonic-boom trail along the beam line
        for (int i = 1; i < RANGE + 7; i++) {
            Vec3 p = origin.add(dir.scale(i));
            sl.sendParticles(ModParticles.GREEN_SONIC_BOOM.get(), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        sl.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 3.0F, 1.3F);

        Vec3 end = origin.add(dir.scale(RANGE));
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, end).inflate(RADIUS),
                x -> x != player && x.isAlive() && x instanceof Enemy && !Factions.isLimbo(x))) {
            // whole-hitbox test so big creatures (boss, golems) are caught, not just centre points;
            // contains(origin) also catches point-blank targets (clip() is empty when origin is inside the box).
            AABB box = e.getBoundingBox().inflate(RADIUS);
            if (box.contains(origin) || box.clip(origin, end).isPresent()) {
                LimboDimension.sendToLimbo(e);
            }
        }
    }
}
