package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Limbo Phantom: a black-and-white phantom three times the size of a normal one, wreathed in white vines
 * with glowing white eyes. It attacks anything NOT of the Limbo faction (so never other phantoms), has 3x HP,
 * hits hard, and -- uniquely -- moves and sees THROUGH bedrock as if it were air, so it can hatch inside a
 * bedrock cube and fly out. When it strikes prey it grabs it: the prey is dragged along and granted the same
 * bedrock-phasing for as long as it is held, letting the phantom haul it out of a sealed cube.
 */
public class LimboPhantomEntity extends Phantom {
    private static final float SCALE = 3.0F;
    private static final int GRAB_DURATION = 100;   // ~5s carry
    private static final int GRAB_COOLDOWN = 60;
    private static final double MELEE_REACH = 1.6;

    private LivingEntity grabbed;
    private int grabTicks;
    private int grabCooldown;
    private int meleeCooldown;

    public LimboPhantomEntity(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
        setPersistenceRequired(); // hatched in a cube -> don't despawn
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.monster.Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)    // 3x a normal phantom (20)
                .add(Attributes.ATTACK_DAMAGE, 9.0)  // hits hard ("can also deal direct damage")
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // phantom flight, circling, sweep attack, sweep damage
        // Target ANY non-limbo creature (mobs + players), not just players.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e != this && e.isAlive() && !Factions.isLimbo(e) && (e instanceof Player || e instanceof Mob)
                        && !(e instanceof Player p && LimboGear.hasFullSet(p)))); // neutral to a full-set wearer
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(SCALE); // 3x hitbox (super already factors phantom-size = 1)
    }

    @Override
    public void tick() {
        long _p = ModProfiler.now();
        // Phase through bedrock: ignore block physics whenever our box is embedded in bedrock, so we can
        // drift out of (or into) a sealed cube; collide normally everywhere else.
        this.noPhysics = touchingBedrock();
        super.tick();
        if (!level().isClientSide()) {
            tickGrab();
            tickMelee();
        }
        ModProfiler.add("limbo.phantom", _p);
    }

    /** Reliable melee: whenever a valid target's hitbox is within reach of ours, strike it (box-to-box gap,
     *  so a giant phantom still connects with a tiny target). */
    private void tickMelee() {
        if (meleeCooldown > 0) { meleeCooldown--; return; }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || Factions.isLimbo(target)) return;
        if (boxGap(getBoundingBox(), target.getBoundingBox()) <= MELEE_REACH) {
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            doHurtTarget(target);
            meleeCooldown = 20;
        }
    }

    /** Squared-free box-to-box gap distance (0 if overlapping) -- the large-mob melee-reach fix. */
    private static double boxGap(AABB a, AABB b) {
        double dx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double dy = Math.max(0.0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
        double dz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && grabbed == null && grabCooldown <= 0 && target instanceof LivingEntity le && canGrab(le)) {
            grabbed = le;
            grabTicks = GRAB_DURATION;
        }
        return hit;
    }

    private boolean canGrab(LivingEntity e) {
        // Grab mobs only -- dragging a player via server-side noPhysics rubber-bands their client.
        return e.isAlive() && !(e instanceof Player) && !Factions.isLimbo(e)
                && e.getBbHeight() < SCALE * 1.5F && !e.isPassenger();
    }

    private void tickGrab() {
        if (grabCooldown > 0) grabCooldown--;
        if (grabbed == null) return;
        if (!grabbed.isAlive() || grabbed.level() != level() || grabTicks-- <= 0) { releaseGrab(); return; }
        // Drag the prey beneath us, phasing it through bedrock too, so we can carry it out of a cube.
        grabbed.noPhysics = true;
        grabbed.fallDistance = 0.0F;
        grabbed.setPos(getX(), getY() - grabbed.getBbHeight() - 0.15, getZ());
        grabbed.setDeltaMovement(Vec3.ZERO);
        grabbed.hurtMarked = true;
    }

    private void releaseGrab() {
        if (grabbed != null) {
            grabbed.noPhysics = false;
            grabbed = null;
            grabCooldown = GRAB_COOLDOWN;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseGrab(); // never strand a grabbed prey in no-physics if we die/unload
        super.remove(reason);
    }

    /** Does our bounding box overlap any bedrock block right now? */
    private boolean touchingBedrock() {
        AABB box = getBoundingBox();
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level().getBlockState(p).is(Blocks.BEDROCK)) return true;
        }
        return false;
    }

    /** See through bedrock: if the only thing blocking the view is bedrock, we can still see the target. */
    @Override
    public boolean hasLineOfSight(Entity entity) {
        if (super.hasLineOfSight(entity)) return true;
        return clearIgnoringBedrock(getEyePosition(), entity.getEyePosition());
    }

    private boolean clearIgnoringBedrock(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double dist = d.length();
        if (dist < 1.0E-4 || dist > 64.0) return dist <= 64.0;
        Vec3 step = d.scale(0.5 / dist);
        int n = (int) (dist / 0.5);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Vec3 p = from;
        for (int i = 0; i < n; i++) {
            p = p.add(step);
            m.set(p.x, p.y, p.z);
            BlockState s = level().getBlockState(m);
            if (s.is(Blocks.BEDROCK) || s.isAir()) continue; // bedrock is transparent to us
            if (s.isViewBlocking(level(), m)) return false;   // a real wall blocks the view
        }
        return true;
    }
}
