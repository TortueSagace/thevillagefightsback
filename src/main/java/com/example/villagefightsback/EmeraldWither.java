package com.example.villagefightsback;

import javax.annotation.Nullable;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Wither: an EMERALD-faction WitherBoss that hunts every non-spared target and shoots skulls like
 * the vanilla wither. Unlike the real boss, its boss bar is suppressed (no player is ever added to the boss
 * event) so it behaves like an ordinary faction mob. Always rendered with the emerald wither texture.
 */
public class EmeraldWither extends WitherBoss implements EmeraldUnit {
    private long glowUntil; // game-time the Glow-Wand forced glow ends (the wither rejects the Glowing EFFECT)

    public EmeraldWither(EntityType<? extends WitherBoss> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        EmeraldMobs.addEmeraldGoals(this, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherBoss.createAttributes();
    }

    /**
     * The wither's side heads pick skull targets by scanning nearby mobs directly (bypassing the standard target
     * selector and the {@code onChangeTarget} faction block), and only fire when {@code canAttack} passes. So gate
     * it here: an emerald wither never attacks a fellow emerald unit or the village faction.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        // Defer to the faction's single source of truth (the same rule the target selector uses), so the skull
        // heads never fire at a SPARED target -- a fellow emerald, the village, OR the vindictive/infected faction
        // (the Vindictive Village Spirit + its guards). Then the wither's own immunities (other withers, etc.) apply.
        return EmeraldMobs.isHostileTarget(target) && super.canAttack(target);
    }

    /**
     * The vanilla {@link WitherBoss#addEffect} rejects EVERY potion effect, which silently swallows the Glow Wand's
     * Glowing outline. Intercept Glowing and drive a forced glow tag instead (which the wither CAN show and which
     * syncs to clients) for the effect's duration, so the wand highlights it like every other emerald unit. All
     * other effects keep the wither's blanket immunity.
     */
    @Override
    public boolean addEffect(MobEffectInstance inst, @Nullable Entity source) {
        if (inst.getEffect() == MobEffects.GLOWING) {
            glowUntil = level().getGameTime() + inst.getDuration();
            setGlowingTag(true);
            return true;
        }
        return super.addEffect(inst, source);
    }

    @Override
    public void tick() {
        super.tick();
        if (glowUntil > 0L && level().getGameTime() >= glowUntil) { // forced glow expired -> clear it
            glowUntil = 0L;
            setGlowingTag(false);
        }
    }

    // Suppress the boss bar: never add a player to the boss event so no boss bar is shown.
    @Override
    public void startSeenByPlayer(ServerPlayer p) {}

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
