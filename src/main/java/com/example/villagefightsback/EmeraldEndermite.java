package com.example.villagefightsback;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Endermite ("emerald mite"): the swarm unit that erupts from infested paths when an emerald unit
 * dies. A hostile combat scout -- it keeps its vanilla attack, gains the emerald AI + the explorer roam (like the
 * cats/bats) so it actively hunts, despawns 1 minute after spawning, and drops no emerald (its loot table is empty).
 */
public class EmeraldEndermite extends Endermite implements EmeraldUnit {
    /** Mites vanish 1 minute (1200 ticks) after spawning -- they are a transient swarm, not a lasting population. */
    private static final int LIFETIME_TICKS = 20 * 60;

    /** An invokespecial handle to {@code Mob.aiStep} (SRG {@code m_8107_}), so we can run the base mob tick while
     *  SKIPPING {@link Endermite#aiStep()} -- whose only extra behaviour is the purple PORTAL particles (client) and
     *  the vanilla 2400-tick despawn (server, which we replace with our own shorter lifetime). Null = fall back. */
    private static final MethodHandle MOB_AISTEP = resolveMobAiStep();

    private static MethodHandle resolveMobAiStep() {
        try {
            Method m = ObfuscationReflectionHelper.findMethod(Mob.class, "m_8107_");
            return MethodHandles.lookup().unreflectSpecial(m, EmeraldEndermite.class);
        } catch (Throwable t) {
            return null; // reflection unavailable -> fall back to super.aiStep() (keeps particles, but functional)
        }
    }

    public EmeraldEndermite(EntityType<? extends Endermite> type, Level level) {
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
        EmeraldMobs.addExplorer(this); // scout: roam to seek out infection targets, like the cats/bats
    }

    @Override
    public void aiStep() {
        // Run Mob.aiStep directly (skipping Endermite.aiStep) so the mite emits NO portal particles.
        if (MOB_AISTEP != null) {
            try {
                MOB_AISTEP.invoke(this);
            } catch (Throwable t) {
                super.aiStep(); // fallback: keep behaviour even if the special-call handle fails
            }
        } else {
            super.aiStep();
        }
        if (!this.level().isClientSide() && this.tickCount >= LIFETIME_TICKS) {
            this.discard(); // silent despawn after 1 minute (no death effects, no drops)
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Large follow range so the mite detects targets from afar and goes hunting (matches the cat/bat scouts).
        return Endermite.createAttributes().add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
