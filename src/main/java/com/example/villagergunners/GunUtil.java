package com.example.villagergunners;

import java.util.List;
import java.util.UUID;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import ttv.migami.jeg.common.Gun;
import ttv.migami.jeg.entity.ai.AIGunEvent;
import ttv.migami.jeg.item.GunItem;

/** Helpers for handing out JEG guns, firing them from mobs, and finding hostiles. */
public final class GunUtil {
    private GunUtil() {}

    public static final ResourceLocation REVOLVER = new ResourceLocation("jeg", "revolver");
    public static final ResourceLocation MINIGUN = new ResourceLocation("jeg", "minigun");
    public static final ResourceLocation FLAMETHROWER = new ResourceLocation("jeg", "flamethrower");
    public static final ResourceLocation ROCKET_LAUNCHER = new ResourceLocation("jeg", "rocket_launcher");

    // Soft integration with Mace Combat Backport (mace_port): its wind charge.
    public static final ResourceLocation WIND_CHARGE = new ResourceLocation("mace_port", "wind_charge");

    // Fixed UUID so re-applying the follow-range boost on world reload is idempotent.
    private static final UUID FOLLOW_RANGE_BOOST_UUID = UUID.fromString("c0ffee00-0000-4000-8000-000000000001");

    /** Build a JEG gun ItemStack by registry id. Returns EMPTY if the item is missing. */
    public static ItemStack makeGun(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Build a JEG gun ItemStack with bonus damage. JEG's performGunAttack adds the stack's
     * "AdditionalDamage" NBT to the projectile's base damage, so this makes the gun hit harder
     * (and partly offsets damage-over-life falloff on guns like the minigun).
     */
    public static ItemStack makeGun(ResourceLocation id, float additionalDamage) {
        ItemStack stack = makeGun(id);
        if (!stack.isEmpty() && additionalDamage != 0.0F) {
            stack.getOrCreateTag().putFloat("AdditionalDamage", additionalDamage);
        }
        return stack;
    }

    /**
     * Snap the shooter to face the target. JEG's getDirection() builds the shot from the entity's
     * own rotation (getXRot/getYRot), NOT a straight line to the target -- and a mob's body yaw lags
     * its head when turning. So we must orient the entity at the target right before firing, or it
     * shoots wherever its body happens to face (the "aims badly" symptom).
     */
    public static void aimAt(Mob shooter, LivingEntity target) {
        // Aim the shooter's eyes at the target's centre of mass (robust hit point). With spread 0 the
        // projectile then flies dead-straight along this rotation.
        shooter.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        shooter.setYHeadRot(shooter.getYRot()); // align head to body so the aim vector is consistent
    }

    /**
     * Fire a JEG gun at a target, server-side. Spawns the projectile(s) via JEG's own firing
     * primitive and plays the gun's fire sound. No ammo is required or consumed.
     *
     * NOTE: call {@link #aimAt} first -- this does not aim, it just fires along the entity's facing.
     */
    public static void fire(Mob shooter, LivingEntity target, ItemStack gunStack, float spread) {
        Level level = shooter.level();
        if (level.isClientSide()) return;
        if (!(gunStack.getItem() instanceof GunItem gunItem)) return;
        Gun gun = gunItem.getModifiedGun(gunStack);
        if (gun == null) return;

        AIGunEvent.performGunAttack(shooter, target, gunStack, gun, spread);

        ResourceLocation fireSound = gun.getSounds() != null ? gun.getSounds().getFire() : null;
        if (fireSound != null) {
            level.playSound(null,
                    shooter.getX(), shooter.getY() + shooter.getEyeHeight(), shooter.getZ(),
                    SoundEvent.createVariableRangeEvent(fireSound),
                    SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        }
    }

    /** True if Mace Combat Backport's wind charge is present (soft dependency). */
    public static boolean windChargeAvailable() {
        return ForgeRegistries.ENTITY_TYPES.containsKey(WIND_CHARGE);
    }

    /**
     * Throw a Mace Combat Backport wind charge straight down at the shooter's feet (for the visual
     * "pogo" effect). Returns true if one was spawned. The actual upward launch is applied directly to
     * the mob by the caller, since iron golems' knockback resistance would otherwise shrug off the burst.
     */
    public static boolean throwWindChargeDown(Mob shooter) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(WIND_CHARGE);
        if (type == null) return false;
        Entity e = type.create(shooter.level());
        if (!(e instanceof AbstractHurtingProjectile proj)) return false;
        proj.setOwner(shooter);
        proj.moveTo(shooter.getX(), shooter.getY() + 0.5, shooter.getZ(), shooter.getYRot(), 90.0F);
        proj.shootFromRotation(shooter, 90.0F, shooter.getYRot(), 0.0F, 1.5F, 0.0F); // 90 pitch = straight down
        shooter.level().addFreshEntity(proj);
        return true;
    }

    /** A gun's fire-rate (ticks between shots) from its data, or {@code fallback}. */
    public static int fireRate(ItemStack gunStack, int fallback) {
        if (gunStack.getItem() instanceof GunItem gunItem) {
            Gun gun = gunItem.getModifiedGun(gunStack);
            if (gun != null && gun.getGeneral() != null) {
                return Math.max(1, gun.getGeneral().getRate());
            }
        }
        return fallback;
    }

    /** Raise FOLLOW_RANGE to at least {@code target} blocks so the mob can detect hostiles that far. */
    public static void extendFollowRange(PathfinderMob mob, double target) {
        AttributeInstance inst = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (inst == null) return;
        double delta = target - inst.getValue();
        if (delta <= 0) return;
        AttributeModifier mod = new AttributeModifier(
                FOLLOW_RANGE_BOOST_UUID, "vgun follow range", delta, AttributeModifier.Operation.ADDITION);
        if (!inst.hasModifier(mod)) {
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
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive() && e != mob
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
}
