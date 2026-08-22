package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.CheapScaffoldManager;
import com.example.villagefightsback.Factions;
import com.example.villagefightsback.GeneralDariusEntity;
import com.example.villagefightsback.GuardWeapons;
import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.NavThrottle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * General Darius's combat brain. Unlike the stage-3/4 guard ({@link StageThreeGuardGoal}) Darius carries NO
 * crossbow: he is a pure melee bruiser wielding the {@link GuardWeapons#alienSmasher() Alien Smasher}.
 *
 * Targeting: he LOCKS onto the single biggest threat in his home brain node's range (highest max-health enemy,
 * or a "dangerous" enemy player) and ignores everything else while closing -- UNLESS another enemy hits him,
 * in which case he kills that attacker then resumes the lock. He keeps the lock even if it leaves the brain's
 * range. When the locked target dies he picks the next biggest (>=50 max HP, or a dangerous player); with none
 * left he walks home and despawns (refunding 64 gems -- handled by {@link GeneralDariusEntity}).
 *
 * Movement: a DOUBLE-height mace jump (no ceiling check -- Darius breaks the ceiling on the way up) to close /
 * smash, a dive smash on the way down, a Cheap-Scaffold pit-escape when boxed in below ground, and -- below
 * 50% HP -- the Limbo banish beam at the locked target on a 30s cooldown (fired by the entity).
 */
public class GeneralDariusGoal extends Goal {
    private static final double MELEE_REACH_SQR = 6.25;     // ~2.5 blocks
    private static final int MELEE_COOLDOWN = 16;
    private static final int RETARGET_INTERVAL = 20;

    private static final double LAUNCH_VY = 2.0;            // ~2x a mace guard's 1.0-1.2 (double jump)
    private static final double LAUNCH_HORIZ = 0.55;
    /** Point-blank: almost straight up, so he lands ON the target rather than behind it. */
    private static final double LAUNCH_HORIZ_CLOSE = 0.12;
    private static final int LAUNCH_COOLDOWN = 35;
    private static final double JUMP_MIN_DIST_SQR = 16.0;   // only jump-close when >4 blocks out
    private static final double SMASH_RANGE_SQR = 9.0;
    private static final float SMASH_BASE = 10.0F;
    private static final float SMASH_PER_BLOCK = 3.5F;
    private static final float FALL_CAP = 24.0F;
    private static final int FIRE_ASPECT_SECONDS = 8;

    private static final int STUCK_TICKS = 40;
    /**
     * A plain hop over whatever the pathfinder could not get round.
     *
     * <p>Until 1.7.2 he closed every gap by leaping, because the leap recharged in 35 ticks. Making that a
     * ten-second SPECIAL took his mobility away with it, and what was left was a walk on a pathfinder that
     * had never had to carry him before -- so he stalled on terrain he used to sail over. This is that
     * mobility back without the damage: no Smasher, no smash, no special spent. It fires only once he has
     * actually stopped making progress, so it is a recovery rather than a habit.
     */
    private static final int HOP_COOLDOWN = 30;
    private static final double HOP_VY = 0.62;
    private static final double HOP_HORIZ = 0.42;
    private int hopCooldown;              // can't progress for this long -> try a scaffold escape
    private static final int SCAFFOLD_INTERVAL = 4;
    /**
     * How far above him a target has to be before he starts building up to it, and how high he will go.
     *
     * <p>He could always dig UP through a ceiling, but a flier in open air has no ceiling to dig -- digUp
     * finds the way already open and declines, and with the leap now a ten-second special there was nothing
     * left to close the gap. So he does what the stage-3 guards do and builds a scaffold column under
     * himself. The cap stops him towering into the sky after something that simply flew away.
     */
    private static final double CLIMB_MIN_ABOVE = 3.0;
    private static final int CLIMB_MAX_HEIGHT = 24;
    private int climbSteps;

    private static final int WITHER_SCAN_INTERVAL = 20;     // re-scan for a wither storm ~1/s (it is the priority)
    private static final double WITHER_HUNT_RANGE = 160.0;  // hunt a wither storm within this (found even hitbox-off)

    private final GeneralDariusEntity darius;
    private LivingEntity locked;
    private LivingEntity witherStorm;                       // cached apex target, re-scanned periodically
    private int witherScanCd;
    private int meleeCooldown, launchCooldown, scaffoldCooldown;
    private int noProgressTicks;
    private double lastDistToTarget = Double.MAX_VALUE;

    public GeneralDariusGoal(GeneralDariusEntity darius) {
        this.darius = darius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return true; // Darius always runs this goal (it also drives the walk-home / despawn path)
    }

    @Override
    public boolean canContinueToUse() {
        return darius.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(darius.level() instanceof ServerLevel sl)) return;
        if (meleeCooldown > 0) meleeCooldown--;
        if (launchCooldown > 0) launchCooldown--;
        if (scaffoldCooldown > 0) scaffoldCooldown--;
        if (hopCooldown > 0) hopCooldown--;
        // Back on his feet -> back to the Obliterator, whether or not the smash actually connected. Before
        // ensureWeapon, so the two never disagree about what should be in his hand this tick.
        if (darius.onGround() && darius.isSmashing()) darius.endSmash();
        ensureWeapon();

        // 0) WITHER STORM gets special treatment: while one is near it is ALWAYS Darius's target, overriding
        //    any lock or retaliation. Found by a hitbox-independent scan, so it works even when the storm has
        //    deactivated its hitbox. Throttled (scanning the whole entity list is not free).
        if (--witherScanCd <= 0) {
            witherScanCd = WITHER_SCAN_INTERVAL;
            witherStorm = Factions.findNearestWitherStorm(sl, darius.position(), WITHER_HUNT_RANGE);
        }
        if (witherStorm != null && witherStorm.isAlive() && Factions.isWitherStorm(witherStorm)) {
            locked = witherStorm;
            engage(sl, witherStorm);
            return;
        }

        // 1) Retaliation override: if something OTHER than the lock just hurt Darius, deal with it first.
        LivingEntity attacker = darius.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && attacker != locked
                && darius.getLastHurtByMobTimestamp() >= darius.tickCount - 100 && isEnemy(attacker)) {
            engage(sl, attacker);
            // when the attacker is dealt with we fall back to the lock automatically (getLastHurtByMob clears)
            return;
        }

        // 2) Maintain the lock.
        if (locked == null || !locked.isAlive() || !isEnemy(locked)) {
            locked = darius.pickBiggestThreat();
            if (locked != null) announceHunt(sl, locked);
            else { walkHomeOrDespawn(sl); return; }
        }

        engage(sl, locked);
    }

    /** Approach + fight a single target: jump-close, dive-smash, melee, with stuck detection for pit escapes. */
    private void engage(ServerLevel sl, LivingEntity t) {
        darius.setTarget(t);
        darius.getLookControl().setLookAt(t, 30.0F, 30.0F);
        double distSqr = darius.distanceToSqr(t);

        // Fire the banish beam at the locked target (the entity enforces the 30s cooldown). Normally gated to
        // below 50% HP, but a wither storm -- the apex threat -- is beamed on sight regardless of Darius's HP.
        if (Factions.isWitherStorm(t) || darius.getHealth() < darius.getMaxHealth() * 0.5F) {
            darius.tryFireBeamAt(t);
        }

        if (!darius.onGround()) {                 // airborne -> steer + dive-smash
            diveAndSmash(t);
            return;
        }

        if (distSqr <= MELEE_REACH_SQR) {
            darius.getNavigation().stop();
            // GOING UP IS THE ATTACK, even point-blank: the smash is worth several times a plain swing and
            // it is what the special is for. Nearly vertical at this range -- the long-range LAUNCH_HORIZ
            // would sail him clean over the target and land him behind it, so a small nudge keeps him above
            // them and diveAndSmash steers the rest of the way down. (This branch existed only on 1.20.1
            // until 1.7.2; the port never had it.)
            if (darius.smashReady() && (t.getY() - darius.getY()) > -2.0) {
                launchAt(t, LAUNCH_HORIZ_CLOSE);
                noProgressTicks = 0;
                return;
            }
            if (meleeCooldown <= 0) {
                darius.stopUsingItem();          // drop the guard to swing
                darius.swing(InteractionHand.MAIN_HAND);
                darius.doHurtTarget(t);
                meleeCooldown = MELEE_COOLDOWN;
            } else if (!darius.isUsingItem()) {
                // Recovering between swings is exactly when a shield is worth holding, and it is what gives
                // the Paladin Shield's reflection any uptime at all. Nothing raises it for him: he is a
                // Monster with a hand-written goal, not a Guard Villagers guard with their shield AI.
                darius.startUsingItem(InteractionHand.OFF_HAND);
            }
            noProgressTicks = 0;
            return;
        }

        // Target ABOVE us behind a ceiling (e.g. Darius is underground and the target is on the surface): tunnel
        // UP toward it -- break the block above his head and rise into the gap. The mirror of the dig-down below.
        // digUp returns false when the way up is already open, so an above-target in open air falls through to the
        // mace jump instead.
        if ((t.getY() - darius.getY()) > 2.0 && digUp(sl)) {
            return;
        }

        // Target in the AIR above him: build up to it. digUp only helps when something is in the way, and
        // a phantom over open ground is exactly the case it declines.
        if ((t.getY() - darius.getY()) > CLIMB_MIN_ABOVE) {
            if (climbToward(sl, t)) return;
        } else {
            climbSteps = 0;      // level with it again: the next flier gets a full allowance
        }

        // Jump-close when comfortably out of reach and the launch is ready (no ceiling check -- breaks through).
        if (distSqr >= JUMP_MIN_DIST_SQR && darius.smashReady()
                && (t.getY() - darius.getY()) > -2.0) {
            launchAt(t);
            return;
        }

        // Target well below us and roughly above its column -> dig straight down to reach it (Darius plows
        // through terrain). The horizontal nav can't descend through solid floors, so we break a shaft.
        double horizSqr = (t.getX() - darius.getX()) * (t.getX() - darius.getX())
                + (t.getZ() - darius.getZ()) * (t.getZ() - darius.getZ());
        if ((darius.getY() - t.getY()) > 2.0 && horizSqr <= 16.0 && digDown(sl)) {
            return;
        }

        // Otherwise walk toward the target -- and hop when the walk is visibly not working.
        NavThrottle.follow(darius, t, 1.2);
        if (hopCooldown <= 0 && noProgressTicks >= STUCK_TICKS / 2 && darius.onGround()
                && (t.getY() - darius.getY()) > -2.0) {
            hop(t);
            return;
        }
        trackProgress(sl, distSqr);
    }

    /** Detect "stuck in a pit" (no closing progress while grounded) and stack cheap scaffolds to climb out. */
    private void trackProgress(ServerLevel sl, double distSqr) {
        if (distSqr + 0.25 < lastDistToTarget) { // made progress
            noProgressTicks = 0;
        } else {
            noProgressTicks++;
        }
        lastDistToTarget = distSqr;
        if (noProgressTicks >= STUCK_TICKS && isBoxedBelowSurface(sl)) {
            scaffoldEscape(sl);
        }
    }

    /** True if Darius is enclosed (a solid block at head height all around / above) and below the open sky. */
    private boolean isBoxedBelowSurface(ServerLevel sl) {
        BlockPos head = darius.blockPosition().above((int) Math.ceil(darius.getBbHeight()) - 1);
        // capped above (cannot just jump out) and not under open sky -> treat as a pit
        return sl.getBlockState(head.above()).blocksMotion() || !sl.canSeeSky(darius.blockPosition());
    }

    /** Pillar up on Cheap Scaffolds (like the limbo raiders) and hop, to climb out of a pit. */
    private void scaffoldEscape(ServerLevel sl) {
        if (scaffoldCooldown > 0) return;
        int gx = darius.getBlockX(), gy = darius.getBlockY(), gz = darius.getBlockZ();
        BlockPos feet = new BlockPos(gx, gy, gz);
        if (sl.getBlockState(feet).canBeReplaced()) {
            sl.setBlock(feet, ModBlocks.CHEAP_SCAFFOLD.get().defaultBlockState(), 3);
            CheapScaffoldManager.track(sl, feet);
        }
        darius.getNavigation().stop();
        darius.setPos(gx + 0.5, gy + 1.0, gz + 0.5); // step up onto the scaffold
        darius.fallDistance = 0.0F;
        scaffoldCooldown = SCAFFOLD_INTERVAL;
        noProgressTicks = STUCK_TICKS - 10; // keep trying to climb a few more steps
    }

    /**
     * Break the block directly under Darius's feet to sink one step closer to a target below him, then drop
     * into the hole. Skips an already-open way down, bedrock (unbreakable), and the village's own phase-door
     * gate blocks (so he never tears the village's gates apart digging). Returns true if it acted this tick.
     */
    private boolean digDown(ServerLevel sl) {
        if (!com.example.villagefightsback.Config.dariusCanBreakBlocks()) return false;

        BlockPos below = darius.blockPosition().below();
        net.minecraft.world.level.block.state.BlockState s = sl.getBlockState(below);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;       // already a way down
        if (s.getDestroySpeed(sl, below) < 0) return false;                // unbreakable (bedrock)
        if (com.example.villagefightsback.LimboGear.isPhaseDoor(s)) return false; // not the gate doors
        darius.getNavigation().stop();
        sl.destroyBlock(below, false);
        darius.fallDistance = 0.0F;
        return true;
    }

    /**
     * Tunnel one step UP toward a target above Darius (he is underground, the target is on the surface): break
     * the block directly above his head and rise into the gap. A pure jump would just drop him back into the
     * same hole, so -- as with the pit escape -- a cheap scaffold is dropped under his feet to give him a floor
     * one block higher. Returns false (so the caller falls back to the mace jump) when the way up is already
     * open air, and skips bedrock and the village's own phase-door gate blocks. Returns true if it acted.
     */
    private boolean digUp(ServerLevel sl) {
        if (!com.example.villagefightsback.Config.dariusCanBreakBlocks()) return false;

        int gx = darius.getBlockX(), gy = darius.getBlockY(), gz = darius.getBlockZ();
        BlockPos head = new BlockPos(gx, gy + 2, gz);                       // the block just above his head (~2 tall)
        net.minecraft.world.level.block.state.BlockState s = sl.getBlockState(head);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;       // already open above -> let him jump
        if (s.getDestroySpeed(sl, head) < 0) return false;                 // unbreakable (bedrock)
        if (com.example.villagefightsback.LimboGear.isPhaseDoor(s)) return false; // not the gate doors
        sl.destroyBlock(head, false);
        BlockPos feet = new BlockPos(gx, gy, gz);
        if (sl.getBlockState(feet).canBeReplaced()) {
            sl.setBlock(feet, ModBlocks.CHEAP_SCAFFOLD.get().defaultBlockState(), 3);
            CheapScaffoldManager.track(sl, feet);
        }
        darius.getNavigation().stop();
        darius.setPos(gx + 0.5, gy + 1.0, gz + 0.5);                       // step up into the cleared space
        darius.fallDistance = 0.0F;
        return true;
    }

    /**
     * Walk under the target, then pillar up on cheap scaffolds until he can reach it.
     *
     * <p>The same trick {@code StageThreeGuardGoal.climbWithScaffold} uses, and the same blocks: they carry
     * no support requirement and expire on their own after about thirty seconds, so a chase into the sky
     * does not leave a tower standing in the village afterwards.
     *
     * @return true when it handled the tick, so the caller stops
     */
    private boolean climbToward(ServerLevel sl, LivingEntity t) {
        if (climbSteps >= CLIMB_MAX_HEIGHT) return false;               // high enough; let something else try
        int gx = darius.getBlockX(), gy = darius.getBlockY(), gz = darius.getBlockZ();
        int hx = (int) Math.floor(t.getX()) - gx, hz = (int) Math.floor(t.getZ()) - gz;
        if (Math.abs(hx) > 1 || Math.abs(hz) > 1) {
            NavThrottle.goTo(darius, t.getX(), darius.getY(), t.getZ(), 1.2);   // get under it first
            return true;
        }
        if (scaffoldCooldown > 0) return true;                         // pacing the climb, not failing it

        BlockPos feet = new BlockPos(gx, gy, gz);
        if (sl.getBlockState(feet.above()).blocksMotion()) return false;   // capped: digUp's problem, not this
        if (sl.getBlockState(feet).canBeReplaced()) {
            sl.setBlock(feet, ModBlocks.CHEAP_SCAFFOLD.get().defaultBlockState(), 3);
            CheapScaffoldManager.track(sl, feet);
        }
        darius.getNavigation().stop();
        darius.setPos(gx + 0.5, gy + 1.0, gz + 0.5);
        darius.fallDistance = 0.0F;
        scaffoldCooldown = SCAFFOLD_INTERVAL;
        climbSteps++;
        return true;
    }

    /** A short vault toward the target. Deliberately NOT launchAt: nothing is spent and nothing is armed. */
    private void hop(LivingEntity t) {
        Vec3 to = t.position().subtract(darius.position());
        double hl = Math.sqrt(to.x * to.x + to.z * to.z);
        double hx = hl > 1.0E-4 ? to.x / hl : 0, hz = hl > 1.0E-4 ? to.z / hl : 0;
        darius.setDeltaMovement(hx * HOP_HORIZ, HOP_VY, hz * HOP_HORIZ);
        darius.hasImpulse = true;
        darius.hurtMarked = true;
        darius.fallDistance = 0.0F;
        hopCooldown = HOP_COOLDOWN;
        noProgressTicks = 0;
    }

    // --- mace jump (double height, no ceiling check) ------------------------------------------------
    private void launchAt(LivingEntity t) {
        launchAt(t, LAUNCH_HORIZ);
    }

    private void launchAt(LivingEntity t, double horiz) {
        Vec3 to = t.position().subtract(darius.position());
        double hl = Math.sqrt(to.x * to.x + to.z * to.z);
        double hx = hl > 1.0E-4 ? to.x / hl : 0, hz = hl > 1.0E-4 ? to.z / hl : 0;
        darius.setDeltaMovement(hx * horiz, LAUNCH_VY, hz * horiz);
        darius.hasImpulse = true;
        darius.hurtMarked = true;
        darius.fallDistance = 0.0F;
        // The entity owns the clock now, not the goal: a goal's fields are lost whenever it restarts,
        // and a special that recharges by breaking line of sight is not a special.
        darius.stopUsingItem();       // you cannot hold a guard and leap at the same time
        darius.beginSmash();
        darius.level().playSound(null, darius.getX(), darius.getY(), darius.getZ(),
                SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.3F, 0.6F);
    }

    private void diveAndSmash(LivingEntity t) {
        Vec3 to = t.position().subtract(darius.position());
        double hl = Math.sqrt(to.x * to.x + to.z * to.z);
        if (hl > 0.5) {
            Vec3 m = darius.getDeltaMovement();
            darius.setDeltaMovement(m.x + (to.x / hl) * 0.08, m.y, m.z + (to.z / hl) * 0.08);
        }
        if (!darius.isSmashing() || darius.smashSpent()) return;   // a bounce is not a new special
        if (!darius.isSmashing() || darius.smashSpent()) return;   // a bounce is not a new special
        boolean descending = darius.getDeltaMovement().y < 0;
        if (descending && darius.distanceToSqr(t) <= SMASH_RANGE_SQR && darius.fallDistance > 0.6F) {
            smash(t);
        }
    }

    private void smash(LivingEntity t) {
        float fall = Math.min(darius.fallDistance, FALL_CAP);
        float dmg = SMASH_BASE + fall * SMASH_PER_BLOCK;
        t.hurt(darius.damageSources().mobAttack(darius), dmg);
        t.igniteForSeconds(FIRE_ASPECT_SECONDS);
        darius.swing(InteractionHand.MAIN_HAND);
        darius.level().playSound(null, darius.getX(), darius.getY(), darius.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9F, 1.3F);
        darius.fallDistance = 0.0F;
        darius.spendSmash();       // one blow; he comes down and goes back to the Obliterator
    }

    // --- walk home + despawn (with refund, handled by the entity) -----------------------------------
    private void walkHomeOrDespawn(ServerLevel sl) {
        darius.setTarget(null);
        // general_darius_remains: once summoned he never returns to the brain node -- he just holds his ground
        // even after every 100+ HP enemy is cleared, instead of marching home and refunding.
        if (com.example.villagefightsback.Config.generalDariusRemains()) { darius.getNavigation().stop(); return; }
        if (darius.getHomeBrain() == null) return; // spawn-egg / admin Darius (no home) -> just idle, don't vanish
        BlockPos home = darius.getHomeBrainPos();
        if (home == null) { darius.refundAndDespawn(); return; } // home is in another dimension -> leave
        if (darius.blockPosition().closerThan(home, 4.0)) {
            darius.refundAndDespawn();
        } else {
            NavThrottle.goTo(darius, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.15);
        }
    }

    // --- helpers ------------------------------------------------------------------------------------
    private void announceHunt(ServerLevel sl, LivingEntity t) {
        Component name = t instanceof Player p ? Component.literal(p.getGameProfile().getName()) : t.getDisplayName();
        sl.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("General Darius hunts ").append(name).append(Component.literal("!")), false);
    }

    private boolean isEnemy(LivingEntity e) {
        // Emerald units are a separate faction the village never fights, so Darius never locks/retaliates on them.
        // See GeneralDariusEntity.isHuntable: isSparedByVillage covers the emeralds AND the other
        // champions, so one Darius never locks onto another.
        if (e == darius || !e.isAlive() || Factions.isInfected(e) || Factions.isSparedByVillage(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return com.example.villagefightsback.Factions.isVillageThreat(e);
    }

    private void ensureWeapon() {
        // The Smasher only while the special is in the air; the Obliterator the rest of the time. Without the
        // smashing test this would rip the Smasher back out of his hand on the very tick he leapt.
        ItemStack want = darius.isSmashing()
                ? GuardWeapons.alienSmasher()
                : new ItemStack(com.example.villagefightsback.ModItems.OBLITERATOR.get());
        if (darius.getMainHandItem().getItem() != want.getItem()) {
            darius.setItemSlot(EquipmentSlot.MAINHAND, want.copy());
            darius.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }
}
