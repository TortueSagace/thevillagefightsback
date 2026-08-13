package com.example.villagefightsback;

import java.util.List;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Helpers for aiming mobs, finding hostiles, and a few soft-dependency integrations. (No longer touches JEG
 *  or any third-party weapon mod -- the wind charge is native now.) */
public final class CombatUtil {
    private CombatUtil() {}

    // From Another World mod namespace. FAW is NOT a dependency, so its "infection" blocks (assimilated/
    // "converted" sculk, gore, tentacles, ...) are matched by namespace string rather than class.
    public static final String FAW_NAMESPACE = "fromanotherworld";

    // Fixed id so re-applying the follow-range boost on world reload is idempotent.
    private static final ResourceLocation FOLLOW_RANGE_BOOST_ID =
            TheVillageFightsBack.id("vgun_follow_range");

    /**
     * Snap the shooter to face the target's centre of mass, so a subsequent straight-line shot (e.g. our
     * native rocket/bullet, which is aimed by hand) lines up with where the turret is visibly looking.
     */
    public static void aimAt(Mob shooter, LivingEntity target) {
        shooter.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        shooter.setYHeadRot(shooter.getYRot()); // align head to body so the aim vector is consistent
    }

    /** True if the (native) wind charge is available. Always true now that it's our own entity. */
    public static boolean windChargeAvailable() {
        return true;
    }

    /**
     * Throw our native wind charge straight down at the shooter's feet (for the visual "pogo" effect).
     * Returns true if one was spawned. The actual upward launch is applied directly to the mob by the
     * caller, since iron golems' knockback resistance would otherwise shrug off the burst.
     */
    public static boolean throwWindChargeDown(Mob shooter) {
        if (shooter.level().isClientSide()) return false;
        WindChargeEntity proj = new WindChargeEntity(shooter.level(), shooter);
        proj.moveTo(shooter.getX(), shooter.getY() + 0.5, shooter.getZ(), shooter.getYRot(), 90.0F);
        proj.shootFromRotation(shooter, 90.0F, shooter.getYRot(), 0.0F, 1.5F, 0.0F); // 90 pitch = straight down
        shooter.level().addFreshEntity(proj);
        return true;
    }

    /** Raise FOLLOW_RANGE to at least {@code target} blocks so the mob can detect hostiles that far. */
    public static void extendFollowRange(PathfinderMob mob, double target) {
        AttributeInstance inst = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (inst == null) return;
        double delta = target - inst.getValue();
        if (delta <= 0) return;
        AttributeModifier mod = new AttributeModifier(
                FOLLOW_RANGE_BOOST_ID, delta, AttributeModifier.Operation.ADD_VALUE);
        if (inst.getModifier(FOLLOW_RANGE_BOOST_ID) == null) {
            inst.addPermanentModifier(mod);
        }
    }

    /**
     * Nearest living hostile within {@code range} that the mob has line of sight to.
     * "Hostile" = implements {@link Enemy}, which covers all vanilla AND modded hostile mobs.
     */
    public static LivingEntity findNearestHostile(Mob mob, double range) {
        double rangeSqr = range * range;
        AABB box = mob.getBoundingBox().inflate(range);
        // Faction-aware: a boss-minion (vindictive) mob hunts anyone not in its own faction; a village mob
        // hunts vanilla hostiles AND the boss faction (so the village fights back against the vindictive).
        boolean mobInfected = Factions.isInfected(mob);
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> (mobInfected ? (e instanceof Enemy && !Factions.isInfected(e))
                                  : Factions.isVillageThreat(e))
                        && (mobInfected || !Factions.isSparedByVillage(e)) // a village mob never targets Darius or the friendly Vindictive Village Spirit
                        && e.isAlive() && e != mob
                        && mob.distanceToSqr(e) <= rangeSqr
                        && mob.getSensing().hasLineOfSight(e));
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = mob.distanceToSqr(e);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        return nearest;
    }

    /** Count living hostiles ({@link Enemy}) within {@code range} blocks of the mob (no line-of-sight needed). */
    public static int countHostilesWithin(Mob mob, double range) {
        double rangeSqr = range * range;
        AABB box = mob.getBoundingBox().inflate(range);
        boolean mobInfected = Factions.isInfected(mob);
        return mob.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> (mobInfected ? (e instanceof Enemy && !Factions.isInfected(e))
                                  : Factions.isVillageThreat(e))
                        && (mobInfected || !Factions.isSparedByVillage(e)) // a village mob never targets Darius or the friendly Vindictive Village Spirit
                        && e.isAlive() && e != mob
                        && mob.distanceToSqr(e) <= rangeSqr).size();
    }

    /**
     * True if the entity is standing on (or inside) a block from From Another World -- i.e. the block it
     * rests on, or the block at its feet, belongs to the {@value #FAW_NAMESPACE} namespace. Matched by
     * namespace so FAW stays a non-dependency; no-ops when FAW is not installed.
     */
    public static boolean isOnFawBlock(Entity e) {
        Level level = e.level();
        return isFawBlock(e.getBlockStateOn()) || isFawBlock(level.getBlockState(e.blockPosition()));
    }

    private static boolean isFawBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && FAW_NAMESPACE.equals(id.getNamespace());
    }

    /**
     * Prime a vanilla TNT block at the igniter's feet, owned by the igniter. Owner attribution means the
     * blast's damage source resolves back to the igniter (an ally), so the mod's ally-immunity in
     * {@code onLivingHurt} spares fellow defenders while the blast still hurts hostiles and clears blocks.
     */
    public static void primeTnt(LivingEntity igniter, int fuse) {
        Level level = igniter.level();
        if (level.isClientSide()) return;
        PrimedTnt tnt = new PrimedTnt(level, igniter.getX(), igniter.getY(), igniter.getZ(), igniter);
        tnt.setFuse(fuse);
        level.addFreshEntity(tnt);
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(),
                net.minecraft.sounds.SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
