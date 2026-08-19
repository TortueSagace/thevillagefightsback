package com.example.villagefightsback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.example.villagefightsback.platform.event.LivingHurtEvent;
import com.example.villagefightsback.platform.EventPriority;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * The void-fall chain. Falling out of the world no longer kills you outright -- you drop through into the next
 * world down:
 *   overworld / nether / any non-End dimension  ->  Limbo (from its top, keeping your downward momentum)
 *   Limbo                                        ->  the End (from its top)
 *   the End                                      ->  death (the only fatal world-fall)
 * So a careless step into the void becomes a long plunge: world -> Limbo -> End -> oblivion.
 */
public final class VoidFallEvents {
    private VoidFallEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onVoidDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        if (!(player.level() instanceof ServerLevel from)) return;

        MinecraftServer server = from.getServer();
        ServerLevel dest;
        if (Level.END.equals(from.dimension())) {
            return; // the End is the bottom of the stack -> let the void kill
        } else if (LimboDimension.LIMBO.equals(from.dimension())) {
            dest = server.getLevel(Level.END);
        } else {
            dest = server.getLevel(LimboDimension.LIMBO);
        }
        if (dest == null) return; // can't relay -> let vanilla handle it

        event.setCanceled(true);   // not dying -- relaying instead
        relayInto(player, from, dest);
    }

    /** Drop {@code player} into the top of {@code dest}, keeping their (downward) momentum, with a whoosh. */
    private static void relayInto(ServerPlayer player, ServerLevel from, ServerLevel dest) {
        Vec3 motion = player.getDeltaMovement();
        double x = player.getX();
        double z = player.getZ();
        double y = dest.getMaxBuildHeight() - 2; // "from the top"

        // exit puff where they vanished
        from.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(), player.getZ(),
                30, 0.4, 0.6, 0.4, 0.4);

        player.teleportTo(dest, x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(motion.x, Math.min(motion.y, -0.6), motion.z); // keep falling
        player.fallDistance = 0.0F; // the inter-world drop itself does no fall damage
        player.hurtMarked = true;   // resync velocity to the client

        dest.sendParticles(ParticleTypes.PORTAL, x, y, z, 40, 0.4, 0.8, 0.4, 0.6);
        dest.sendParticles(ParticleTypes.SOUL, x, y, z, 12, 0.4, 0.4, 0.4, 0.05);
        dest.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
    }
}
