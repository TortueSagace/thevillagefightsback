package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Bat: a bat reskinned into the EMERALD faction. As a normally-passive species it gains a
 * melee attack (50% of its max health) via {@link EmeraldMobs}, and it hunts every non-spared target.
 * A Bat is a plain {@link Mob} (not a PathfinderMob), so the shared wiring only adds a target scan.
 */
public class EmeraldBat extends Bat implements EmeraldUnit {
    public EmeraldBat(EntityType<? extends Bat> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return true;
    }

    private int emeraldAttackCooldown = 0;

    @Override
    protected void registerGoals() {
        super.registerGoals();
        EmeraldMobs.addEmeraldGoals(this, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Large follow range so the flitting bat detects (and homes in on) infectable mobs from afar.
        // ATTACK_DAMAGE is registered here; EmeraldMobs overwrites it to 50% of max HP.
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0D).add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    /** A vanilla bat has no attack and flits at random, so steer the emerald bat toward its target and bite it on
     *  contact (it gets a target from the shared NearestAttackableTargetGoal / the central retaliation handler). */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level.isClientSide()) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        net.minecraft.world.phys.Vec3 to = target.position()
                .add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(this.position());
        double dist = to.length();
        if (dist > 0.01D) this.setDeltaMovement(this.getDeltaMovement().add(to.scale(0.05D / dist)));
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.emeraldAttackCooldown > 0) this.emeraldAttackCooldown--;
        if (dist < 1.6D && this.emeraldAttackCooldown == 0) {
            this.doHurtTarget(target);
            this.emeraldAttackCooldown = 20;
        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
