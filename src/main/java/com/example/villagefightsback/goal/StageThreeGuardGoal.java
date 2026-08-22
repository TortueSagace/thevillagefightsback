package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.GuardWeapons;
import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.WindChargeEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The stage-3 guard's combat brain. Picks one of three modes each tick by range + headroom:
 *   target &gt; 10 blocks         -> CROSSBOW: fire emerald-virus tipped arrows.
 *   target &le; 10 + room above  -> MACE: wind-charge launch -> dive smash -> Wind-Burst bounce to the next
 *                                   target; water-clutches on the way down to negate fall damage.
 *   target &le; 10 + low ceiling -> SLASHER: just melee with the Alien Slasher like a villager, until there
 *                                   is room to bounce or the target backs off to crossbow range.
 */
public class StageThreeGuardGoal extends Goal {
    /** Arrows tagged with this in their persistent data infect the Emerald Virus on hit (see EmeraldVirusEvents). */
    public static final String NBT_VIRUS_ARROW = "emerald_virus_arrow";

    private static final double FOLLOW_RANGE = 24.0;
    private static final double CROSSBOW_RANGE = 10.0;   // > this -> crossbow
    private static final int MIN_HEADROOM = 6;            // air blocks above the head needed to bounce
    private static final int RETARGET_INTERVAL = 10;

    private static final int FIRE_INTERVAL = 25;          // crossbow cadence (Quick Charge III ~ fast)
    private static final float ARROW_SPEED = 2.8F;

    private static final double LAUNCH_VY = 1.2;          // wind-charge upward kick (~8 blocks)
    private static final double LAUNCH_HORIZ = 0.45;      // lean toward the target
    private static final int LAUNCH_COOLDOWN = 25;
    private static final double SMASH_RANGE_SQR = 9.0;    // smash when within 3 blocks while diving
    private static final float MACE_BASE = 7.0F;
    private static final float SMASH_PER_BLOCK = 3.0F;    // Density-3 boosted fall scaling
    private static final float FALL_CAP = 20.0F;
    private static final int FIRE_ASPECT_SECONDS = 8;     // Fire Aspect 2

    private static final double SAFE_FALL = 3.0;          // water-clutch above this fall distance
    private static final int CLUTCH_REMOVE_TICKS = 25;

    private static final double REACH_SQR = 4.0;          // slasher melee reach
    private static final int MELEE_COOLDOWN = 20;

    private static final double CLIMB_THRESHOLD = 3.0;    // target this far above -> climb cheap scaffolds to reach it
    private static final int CLIMB_INTERVAL = 3;          // ticks per scaffold step (a visible climb, not a teleport)

    private final PathfinderMob guard;
    private LivingEntity target;
    private int fireCooldown, launchCooldown, meleeCooldown, climbCooldown;
    private BlockPos clutchPos;
    private int clutchTimer;

    public StageThreeGuardGoal(PathfinderMob guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        refreshTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return (target != null && target.isAlive()) || !guard.onGround(); // stay active mid-bounce
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        guard.setTarget(null);
        target = null;
        removeClutch();
    }

    @Override
    public void tick() {
        if (fireCooldown > 0) fireCooldown--;
        if (launchCooldown > 0) launchCooldown--;
        if (meleeCooldown > 0) meleeCooldown--;
        if (climbCooldown > 0) climbCooldown--;
        tickClutch();

        // On fire -> the same water trick used to clutch a landing, but to douse the flames (stage-3 guards
        // know how to MLG a fire out). Places a brief water source at the feet that extinguishes them.
        if (guard.isOnFire() && !guard.fireImmune()) extinguishWithWater();

        if (guard.tickCount % RETARGET_INTERVAL == 0) refreshTarget();
        LivingEntity t = target;
        if (t == null) {                       // nothing to fight: just clutch a landing if we were bouncing
            if (!guard.onGround()) maybeWaterClutch();
            return;
        }
        guard.setTarget(t);
        guard.getLookControl().setLookAt(t, 30.0F, 30.0F);

        // The mace-bounce (launch / dive / smash) is a STAGE-3+ ability. A stage-2 "virus archer" guard has no mace,
        // so it sticks to crossbow + Alien Slasher. Vindictive guards keep the mace-bounce regardless.
        boolean hasMace = com.example.villagefightsback.UpgradeState.isUpgraded(guard)
                || guard instanceof com.example.villagefightsback.VindictiveGuardEntity;
        double dist = guard.distanceTo(t);
        if (t instanceof Creeper) {            // never melee a creeper -- shoot it with the crossbow at any range
            crossbow(t, dist);
        } else if (hasMace && !guard.onGround()) { // already airborne -> keep diving / smashing / clutching
            diveAndSmash(t);
        } else if (dist > CROSSBOW_RANGE) {
            crossbow(t, dist);
        } else if (t.getY() - guard.getY() > CLIMB_THRESHOLD) {
            climbWithScaffold(t, dist);         // reach a target well above us with cheap scaffolds (NOT wind charge)
        } else if (hasMace && hasHeadroom()) {
            launchAt(t);                        // begin a bounce (wind charge = attack only)
        } else {
            slasher(t, dist);                   // low ceiling / stage 2 -> villager-style melee
        }
    }

    // --- CROSSBOW -------------------------------------------------------------------------------
    private void crossbow(LivingEntity t, double dist) {
        setMainhand(GuardWeapons.crossbow());
        if (dist > 18.0) com.example.villagefightsback.NavThrottle.follow(guard, t, 1.0); else guard.getNavigation().stop();
        if (fireCooldown <= 0 && guard.getSensing().hasLineOfSight(t)) {
            fireVirusArrow(t);
            fireCooldown = FIRE_INTERVAL;
        }
    }

    private void fireVirusArrow(LivingEntity t) {
        Level level = guard.level();
        // green "poison-arrow" look: tint the vanilla tipped-arrow texture emerald (no vanilla potion attached).
        // The Arrow now derives its color/effects from its pickup ItemStack's POTION_CONTENTS (the old
        // setEffectsFromItem is gone), so build the tipped arrow first and hand it in as the pickup stack.
        ItemStack tip = new ItemStack(Items.TIPPED_ARROW);
        tip.set(DataComponents.POTION_CONTENTS,
                new PotionContents(java.util.Optional.empty(), java.util.Optional.of(0x2ECC40), java.util.List.of()));
        // Pass the (Piercing IV) crossbow as the firedFromWeapon so vanilla applies Piercing IV (the old
        // setPierceLevel is now private; pierce comes from the firing weapon's Piercing enchant).
        Arrow arrow = new Arrow(level, guard, tip, GuardWeapons.crossbow());
        double dx = t.getX() - guard.getX();
        double dy = t.getY(0.5) - arrow.getY();
        double dz = t.getZ() - guard.getZ();
        double dh = Math.sqrt(dx * dx + dz * dz);
        // Gravity-compensation lob. Vanilla skeletons use 0.2 * horizontal-dist at bow speed 1.6; arrow drop
        // scales with flight-time^2, so for our faster ARROW_SPEED the factor is 0.2 * (1.6/2.8)^2 ~= 0.065.
        // (The old 0.18 was tuned for a slow bow and lobbed every shot clean over the target's head.)
        arrow.shoot(dx, dy + dh * 0.065, dz, ARROW_SPEED, 1.0F);
        arrow.getPersistentData().putBoolean(NBT_VIRUS_ARROW, true); // applies Emerald Virus on hit
        arrow.setBaseDamage(arrow.getBaseDamage() + 2.0);     // a bit punchier than a plain arrow
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.playSound(null, guard.getX(), guard.getY(), guard.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
        level.addFreshEntity(arrow);
        guard.swing(InteractionHand.MAIN_HAND);
    }

    // --- MACE bounce ----------------------------------------------------------------------------
    private void launchAt(LivingEntity t) {
        setMainhand(GuardWeapons.mace());
        if (guard.distanceToSqr(t) > 16.0) { com.example.villagefightsback.NavThrottle.follow(guard, t, 1.1); return; } // close in first
        if (launchCooldown > 0) return;
        Vec3 to = t.position().subtract(guard.position());
        double hl = Math.sqrt(to.x * to.x + to.z * to.z);
        double hx = hl > 1.0E-4 ? to.x / hl : 0, hz = hl > 1.0E-4 ? to.z / hl : 0;
        guard.setDeltaMovement(hx * LAUNCH_HORIZ, LAUNCH_VY, hz * LAUNCH_HORIZ);
        guard.hasImpulse = true;
        guard.hurtMarked = true;
        guard.fallDistance = 0.0F;
        launchCooldown = LAUNCH_COOLDOWN;
        guard.level().playSound(null, guard.getX(), guard.getY(), guard.getZ(),
                SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.2F, 0.7F);
        throwLaunchWindCharge(); // a gust visibly bursts under the guard as it leaps (visual only)
    }

    private void diveAndSmash(LivingEntity t) {
        setMainhand(GuardWeapons.mace());
        // steer horizontally toward the target while airborne
        Vec3 to = t.position().subtract(guard.position());
        double hl = Math.sqrt(to.x * to.x + to.z * to.z);
        if (hl > 0.5) {
            Vec3 m = guard.getDeltaMovement();
            guard.setDeltaMovement(m.x + (to.x / hl) * 0.06, m.y, m.z + (to.z / hl) * 0.06);
        }
        boolean descending = guard.getDeltaMovement().y < 0;
        if (descending && guard.distanceToSqr(t) <= SMASH_RANGE_SQR && guard.fallDistance > 0.6F) {
            smash(t);
        } else if (descending) {
            maybeWaterClutch();
        }
    }

    private void smash(LivingEntity t) {
        float fall = Math.min(guard.fallDistance, FALL_CAP);
        float dmg = MACE_BASE + fall * SMASH_PER_BLOCK; // my own Density-3 smash formula (mob-friendly)
        t.hurt(guard.damageSources().mobAttack(guard), dmg);
        t.igniteForSeconds(FIRE_ASPECT_SECONDS);
        guard.swing(InteractionHand.MAIN_HAND);
        guard.level().playSound(null, guard.getX(), guard.getY(), guard.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.4F);
        guard.fallDistance = 0.0F;
        // Wind Burst 3: bounce toward the next live target, else fall and clutch.
        LivingEntity next = nearestEnemy(CROSSBOW_RANGE + 2.0, t.isAlive() ? null : t, false); // don't bounce onto a creeper
        if (next != null && hasHeadroom()) {
            target = next;
            Vec3 to = next.position().subtract(guard.position());
            double hl = Math.sqrt(to.x * to.x + to.z * to.z);
            double hx = hl > 1.0E-4 ? to.x / hl : 0, hz = hl > 1.0E-4 ? to.z / hl : 0;
            guard.setDeltaMovement(hx * LAUNCH_HORIZ, LAUNCH_VY * 0.85, hz * LAUNCH_HORIZ);
            guard.hasImpulse = true;
            guard.hurtMarked = true;
            throwLaunchWindCharge(); // gust burst under the guard on the Wind-Burst bounce (visual only)
        }
    }

    /** MLG-style: drop a water source under the feet so a high fall does no damage; picked back up shortly. */
    private void maybeWaterClutch() {
        if (clutchPos != null || guard.fallDistance <= SAFE_FALL) return;
        BlockPos feet = guard.blockPosition();
        Level level = guard.level();
        // only when the ground is close below (about to land)
        BlockPos ground = feet;
        for (int i = 0; i <= 2; i++) {
            if (!level.getBlockState(ground.below()).isAir()) break;
            ground = ground.below();
        }
        if (feet.getY() - ground.getY() > 2) return; // still high up -> keep falling
        // See WaterClutchEvents.placeClutch: clutching INTO water holes the player's pond.
        if (!level.getBlockState(feet).getFluidState().isEmpty()) return;
        if (!level.getBlockState(feet).canBeReplaced()) return;
        level.setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
        clutchPos = feet.immutable();
        clutchTimer = CLUTCH_REMOVE_TICKS;
        guard.fallDistance = 0.0F;
    }

    /** Douse the guard's flames the same way it clutches a landing: a brief water source at the feet. */
    private void extinguishWithWater() {
        if (clutchPos != null) return; // a clutch (landing or fire) is already down
        BlockPos feet = guard.blockPosition();
        Level level = guard.level();
        // Same rule, and doubly pointless here: a guard standing in water is not on fire.
        if (!level.getBlockState(feet).getFluidState().isEmpty()) return;
        if (!level.getBlockState(feet).canBeReplaced()) return;
        level.setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
        clutchPos = feet.immutable();
        clutchTimer = CLUTCH_REMOVE_TICKS;
        guard.clearFire(); // out instantly; the water just makes the trick visible
        level.playSound(null, guard.getX(), guard.getY(), guard.getZ(),
                SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 0.7F, 1.2F);
    }

    private void tickClutch() {
        if (clutchPos == null) return;
        if (--clutchTimer <= 0) removeClutch();
    }

    private void removeClutch() {
        if (clutchPos == null) return;
        if (guard.level().getBlockState(clutchPos).is(Blocks.WATER)) {
            guard.level().setBlock(clutchPos, Blocks.AIR.defaultBlockState(), 3);
        }
        clutchPos = null;
    }

    // --- SLASHER (low ceiling fallback) ---------------------------------------------------------
    private void slasher(LivingEntity t, double dist) {
        // Stage-4 guards wield the Alien Eradicator (Virus II); vindictive guards keep the plain slasher (Virus I).
        boolean eradicate = com.example.villagefightsback.UpgradeState.isStage4(guard)
                && !(guard instanceof com.example.villagefightsback.VindictiveGuardEntity);
        setMainhand(eradicate ? GuardWeapons.eradicator() : GuardWeapons.slasher());
        com.example.villagefightsback.NavThrottle.follow(guard, t, 1.15);
        if (guard.distanceToSqr(t) <= REACH_SQR && meleeCooldown <= 0) {
            guard.swing(InteractionHand.MAIN_HAND);
            guard.doHurtTarget(t); // Alien Slasher on-hit payload is applied by EmeraldVirusEvents
            meleeCooldown = MELEE_COOLDOWN;
        }
    }

    // --- climb to reach a high target (cheap scaffolds, NOT the wind charge) ---------------------
    private void climbWithScaffold(LivingEntity t, double dist) {
        setMainhand(GuardWeapons.mace());
        int gx = guard.getBlockX(), gy = guard.getBlockY(), gz = guard.getBlockZ();
        int hx = (int) Math.floor(t.getX()) - gx, hz = (int) Math.floor(t.getZ()) - gz;
        if (Math.abs(hx) > 1 || Math.abs(hz) > 1) {       // walk under the target's column first
            com.example.villagefightsback.NavThrottle.goTo(guard, t.getX(), guard.getY(), t.getZ(), 1.15);
            return;
        }
        if (climbCooldown > 0) return;
        Level level = guard.level();
        BlockPos feet = new BlockPos(gx, gy, gz);
        if (level.getBlockState(feet.above()).blocksMotion()) { crossbow(t, dist); return; } // capped above -> shoot it
        if (level.getBlockState(feet).canBeReplaced()) {
            level.setBlock(feet, ModBlocks.CHEAP_SCAFFOLD.get().defaultBlockState(), 3);
        }
        guard.getNavigation().stop();
        guard.setPos(gx + 0.5, gy + 1.0, gz + 0.5);       // step onto the scaffold (it self-expires in ~30s)
        guard.fallDistance = 0.0F;
        climbCooldown = CLIMB_INTERVAL;
    }

    /**
     * Hurl a COSMETIC wind charge straight down at the floor under the guard as it leaps, so a gust visibly
     * bursts beneath it (more realistic mace-jump). It is visual only -- knockback and damage are suppressed
     * (see {@link WindChargeEntity#setCosmetic}) -- so it never double-launches the guard (the leap is the
     * hand-tuned impulse above) nor tosses nearby allies. The charge ignores its owner, so it passes through
     * the guard and bursts on the ground a tick or two later.
     */
    private void throwLaunchWindCharge() {
        Level level = guard.level();
        if (level.isClientSide()) return;
        WindChargeEntity charge = new WindChargeEntity(level, guard).setCosmetic(true);
        // Spawn at the FEET (not the eyes) and fire straight down, so the gust bursts directly below the guard.
        charge.setPos(guard.getX(), guard.getY() + 0.1, guard.getZ());
        charge.shootFromRotation(guard, 90.0F, 0.0F, 0.0F, 1.2F, 0.0F); // pitch 90 = straight down, dead centre
        level.addFreshEntity(charge);
    }

    // --- shared ---------------------------------------------------------------------------------
    private void setMainhand(ItemStack template) {
        if (guard.getMainHandItem().getItem() != template.getItem()) {
            guard.setItemSlot(EquipmentSlot.MAINHAND, template.copy());
            guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    private boolean hasHeadroom() {
        if (!GuardWeapons.hasMace()) return false; // no mace -> never "bounce"; melee instead
        BlockPos head = BlockPos.containing(guard.getX(), guard.getEyeY(), guard.getZ());
        for (int i = 1; i <= MIN_HEADROOM; i++) {
            if (!guard.level().getBlockState(head.above(i)).isAir()) return false;
        }
        return true;
    }

    private void refreshTarget() {
        // Honor an externally-set target first (a vindictive guard's broad target goal sets guard.getTarget()
        // to villagers/players/animals -- things that aren't Enemy), as long as it's valid and not a faction-mate.
        LivingEntity ext = guard.getTarget();
        if (ext != null && ext.isAlive() && !isFactionMate(ext)
                && guard.distanceToSqr(ext) <= FOLLOW_RANGE * FOLLOW_RANGE) { target = ext; return; }
        if (target != null && target.isAlive() && !isFactionMate(target)
                && guard.distanceToSqr(target) <= FOLLOW_RANGE * FOLLOW_RANGE) return;
        target = nearestEnemy(FOLLOW_RANGE, null, true); // include creepers -- the crossbow can shoot them
    }

    /** A faction-mate this guard must never fight -- only when the guard itself is infected (boss / vindictive guard). */
    private boolean isFactionMate(LivingEntity e) {
        return com.example.villagefightsback.Factions.isInfected(guard) && com.example.villagefightsback.Factions.isInfected(e);
    }

    private LivingEntity nearestEnemy(double radius, LivingEntity exclude, boolean includeCreepers) {
        AABB box = guard.getBoundingBox().inflate(radius);
        LivingEntity best = null;
        double bestD = radius * radius;
        for (LivingEntity e : guard.level().getEntitiesOfClass(LivingEntity.class, box,
                x -> x != guard && x != exclude
                        && com.example.villagefightsback.Factions.isVillageThreat(x)
                        && !com.example.villagefightsback.Factions.isSparedByVillage(x) // never Darius or the friendly Vindictive Village Spirit
                        && !isFactionMate(x)
                        && (includeCreepers || !(x instanceof Creeper)) && x.isAlive())) {
            double d = guard.distanceToSqr(e);
            if (d <= bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /** Cast helper kept for clarity where a ServerLevel is genuinely required. */
    @SuppressWarnings("unused")
    private ServerLevel server() {
        return (ServerLevel) guard.level();
    }
}
