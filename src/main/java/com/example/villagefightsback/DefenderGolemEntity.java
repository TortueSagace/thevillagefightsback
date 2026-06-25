package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

/**
 * The "Defender Golem": the stage-2 evolution of the village iron golem. It is a full iron golem (same body,
 * same melee punch) whose head is replaced by a Wall Defender sealed in a mini protecting-glass cocoon (drawn
 * by {@link com.example.villagefightsback.client.DefenderGolemHeadLayer}). 150 HP. On top of the golem's
 * melee it fires the wall defender's revolver bullet at its current target on a cooldown -- a ranged attack
 * that triggers INDEPENDENTLY of the melee, exactly like a {@link WallDefenderEntity}.
 *
 * It is its own creature type (not a renamed vanilla golem), like {@link GigachadGolemEntity}. A village iron
 * golem becomes a Defender Golem at brain-node stage 2 ({@link VillageEvents#convertToDefenderGolem}); a
 * Defender Golem becomes a {@link GigachadGolemEntity} at stage 4 (see {@link UpgradeEvents}).
 */
public class DefenderGolemEntity extends IronGolem {
    private static final double RANGE = 24.0;          // same reach as the wall defender's revolver
    private static final int FIRE_INTERVAL = 30;       // one bullet per 1.5s (fired alongside its melee punch)
    private static final float BULLET_DAMAGE = 10.0F;  // same as the wall defender revolver

    private int fireCooldown;

    public DefenderGolemEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    /** 150 HP; everything else inherited from the vanilla iron golem (melee, movement, target goals). */
    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 150.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();                       // keep the full iron golem behaviour (melee, look, offer flower)
        if (level.isClientSide) return;
        if (fireCooldown > 0) { fireCooldown--; return; }
        // Fire from the head turret at whatever the inherited iron-golem target goals picked (hostiles, and
        // Enemy-of-the-Village players the village rallies it against). Independent of the melee goal.
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceToSqr(target) > RANGE * RANGE) return;
        if (!getSensing().hasLineOfSight(target)) return;
        // Fire from the wall-defender TURRET HEAD (the rendered cocoon high on the golem), not the golem's
        // eye, so the glowing tracer visibly leaves the head. A small forward nudge clears the body.
        net.minecraft.world.phys.Vec3 head = new net.minecraft.world.phys.Vec3(
                getX(), getY() + getBbHeight() * 0.92, getZ());
        net.minecraft.world.phys.Vec3 to = target.getBoundingBox().getCenter().subtract(head);
        net.minecraft.world.phys.Vec3 muzzle = head.add(
                new net.minecraft.world.phys.Vec3(to.x, 0.0, to.z).normalize().scale(0.4));
        BulletEntity.fireFrom(this, muzzle, target, BULLET_DAMAGE);
        fireCooldown = FIRE_INTERVAL;
    }
}
