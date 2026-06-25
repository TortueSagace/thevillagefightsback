package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Endermite ("emerald mite"): the swarm unit that erupts from infested paths when an emerald unit
 * dies. A hostile combat scout -- it keeps an endermite's stats + melee, gains the emerald AI + the explorer
 * roam (like the cats/bats) so it actively hunts, despawns 1 minute after spawning, and drops nothing.
 *
 * <p>It extends {@link Monster} (NOT {@code Endermite}) on purpose: the vanilla endermite spews purple portal
 * particles every client tick, and there is no clean way to keep that tick (needed for movement interpolation)
 * while suppressing only the particles. So we rebuild it from a plain Monster with the endermite's stats/goals
 * and render it with the vanilla endermite model -- same look, no particles.
 */
public class EmeraldEndermite extends Monster implements EmeraldUnit {
    /** Mites vanish 1 minute (1200 ticks) after spawning -- they are a transient swarm, not a lasting population. */
    private static final int LIFETIME_TICKS = 20 * 60;

    public EmeraldEndermite(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false)); // the endermite's bite
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        EmeraldMobs.addEmeraldGoals(this, false); // emerald targeting (isHostileTarget) + HurtByTarget
        EmeraldMobs.addExplorer(this);            // scout: roam to seek out infection targets, like the cats/bats
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Endermite stats (8 HP, 0.25 speed, 2 attack) + a large follow range so it detects targets from afar.
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    public void aiStep() {
        super.aiStep(); // Monster.aiStep -> full movement/AI, and (unlike Endermite) spawns NO particles
        if (!this.level().isClientSide() && this.tickCount >= LIFETIME_TICKS) {
            this.discard(); // silent despawn after 1 minute (no death effects, no drops)
        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
