package com.example.villagefightsback;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * What the two Limbo shields do when they actually stop something.
 *
 * <p>One handler for both, on {@link LivingShieldBlockEvent}, which fires only once vanilla has already
 * decided the block was valid -- the shield was raised, it was facing the right way, and the source was not
 * bypassing it. That is the whole reason the logic lives here rather than on {@code LivingAttackEvent} like
 * the Special Sauce: none of those three tests has to be rewritten, and a shield that reflected damage it
 * had not actually blocked would be a very different item.
 *
 * <p>The event is deliberately NOT cancelled. Cancelling it means "the shield failed", which would undo the
 * damage reduction the player raised it for.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ShieldBashEvents {
    private ShieldBashEvents() {}

    /** The Brain Node's own numbers (see {@link NodeGuard}): about four blocks up and four out. */
    private static final double LAUNCH_UP = 0.78;
    private static final double LAUNCH_OUT = 0.62;

    /** The enchanted-golden-apple package, in ticks. */
    private static final int ABSORB_TICKS = 20 * 120;
    private static final int REGEN_TICKS = 20 * 20;
    private static final int RESIST_TICKS = 20 * 300;

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();
        if (!(blocker.level() instanceof ServerLevel sl)) return;

        // Whichever hand it is in. A guard carries it offhand, a player may hold it either way, and
        // getUseItem() is the one that is actually raised.
        ItemStack shield = blocker.getUseItem();
        if (!(shield.getItem() instanceof LimboShieldItem)) {
            shield = blocker.getOffhandItem();
            if (!(shield.getItem() instanceof LimboShieldItem)) return;
        }
        boolean paladin = shield.getItem() instanceof PaladinShieldItem;

        DamageSource source = event.getDamageSource();
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        // A projectile is "ranged" even when its owner is standing next to you: what was blocked is the
        // arrow, and an arrow cannot be shoved back.
        boolean ranged = direct != null && direct != attacker;

        if (!ranged && attacker != null) {
            bash(sl, blocker, attacker);
        }

        if (paladin) {
            if (attacker instanceof LivingEntity victim && victim != blocker) {
                float back = event.getBlockedDamage() * PaladinShieldItem.REFLECT;
                if (back > 0.0F) {
                    // Both details are load-bearing, and both are borrowed from the Special Sauce: clearing
                    // the i-frames so a fast attacker's own hit is not swallowed, and reflecting the SAME
                    // source so the attacker is its own entity -- which is what stops this bouncing forever
                    // between two shield bearers.
                    victim.invulnerableTime = 0;
                    victim.hurt(source, back);
                }
            }
            feed(blocker);
        }
    }

    /** The Brain Node's shove, aimed away from the shield rather than away from a block. */
    private static void bash(ServerLevel sl, LivingEntity blocker, Entity attacker) {
        Vec3 away = attacker.position().subtract(blocker.position());
        double hl = Math.sqrt(away.x * away.x + away.z * away.z);
        double hx = hl > 1.0E-4 ? away.x / hl : 0.0;
        double hz = hl > 1.0E-4 ? away.z / hl : 0.0;

        attacker.setDeltaMovement(hx * LAUNCH_OUT, LAUNCH_UP, hz * LAUNCH_OUT);
        attacker.hasImpulse = true;
        attacker.hurtMarked = true;   // without this the server's velocity change never reaches the client

        sl.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                attacker.getX(), attacker.getY() + 0.5, attacker.getZ(), 18, 0.3, 0.2, 0.3, 0.05);
    }

    /**
     * What an enchanted golden apple gives, given to whoever is holding the shield.
     *
     * <p>Not gated on the bearer being a player: a mob carrying one gets the same, which is the whole point
     * of General Darius carrying it.
     */
    private static void feed(LivingEntity bearer) {
        bearer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ABSORB_TICKS, 3, false, false, true));
        bearer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 1, false, false, true));
        bearer.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, RESIST_TICKS, 0, false, false, true));
        bearer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESIST_TICKS, 0, false, false, true));
    }
}
