package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The wall defender's brains: 100 HP, scans for a hostile within range (the same {@code Enemy} notion the
 * villagers/guards use) and fires one ~10-damage arrow per second. Line of sight is checked from just
 * OUTSIDE its own protecting-glass shell, and the shot is spawned in the shell so the one-way glass lets it
 * out. Projectiles that reach the defender block reduce its HP (see {@link WallDefenderEvents}); at 0 it
 * breaks.
 */
public class WallDefenderBlockEntity extends BlockEntity {
    public static final int MAX_HP = 100;
    private static final double RANGE = 24.0;
    private static final int FIRE_INTERVAL = 20;      // one shot per second
    private static final float SHOT_SPEED = 3.0F;
    private static final double SHOT_BASE_DAMAGE = 3.3; // ceil(speed*base) ~= 10 damage at SHOT_SPEED

    private int hp = MAX_HP;
    private int cooldown;

    public WallDefenderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALL_DEFENDER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WallDefenderBlockEntity be) {
        if (be.cooldown > 0) { be.cooldown--; return; }
        LivingEntity target = be.acquireTarget(level, pos);
        if (target == null) return;
        be.fire(level, pos, target);
        be.cooldown = FIRE_INTERVAL;
    }

    private LivingEntity acquireTarget(Level level, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        AABB box = new AABB(pos).inflate(RANGE);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                x -> x instanceof Enemy && x.isAlive())) {
            double d = e.distanceToSqr(c.x, c.y, c.z);
            if (d > RANGE * RANGE || d >= bestD) continue;
            if (!canTarget(level, c, e)) continue;
            best = e;
            bestD = d;
        }
        return best;
    }

    /** Line of sight that ignores our own protecting-glass shell by starting ~2 blocks out (past the pane). */
    private boolean canTarget(Level level, Vec3 from, LivingEntity t) {
        Vec3 te = t.getEyePosition();
        Vec3 dir = te.subtract(from);
        double len = dir.length();
        if (len < 2.0) return true;
        Vec3 start = from.add(dir.scale(2.0 / len)); // skip the 1-thick shell
        HitResult hit = level.clip(new ClipContext(start, te,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void fire(Level level, BlockPos pos, LivingEntity target) {
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 aim = target.getEyePosition().subtract(c);
        Vec3 dir = aim.normalize();
        Vec3 spawn = c.add(dir); // one block out -> in the shell cell; the one-way glass lets it pass outward
        Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z);
        arrow.shoot(aim.x, aim.y, aim.z, SHOT_SPEED, 0.0F);
        arrow.setNoGravity(true);                 // straight bullet line: reliably exits the shell, no arc
        arrow.setBaseDamage(SHOT_BASE_DAMAGE);
        arrow.setCritArrow(false);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
        level.playSound(null, pos, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 0.7F, 1.5F);
    }

    /** Take damage from a projectile that reached us; break the block when HP runs out. */
    public void hurt(int amount) {
        if (level == null || level.isClientSide) return;
        hp -= amount;
        setChanged();
        if (hp <= 0) {
            level.destroyBlock(worldPosition, false);
            level.playSound(null, worldPosition, SoundEvents.CONDUIT_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
    }

    public int getHp() { return hp; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("hp", hp);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        hp = tag.contains("hp") ? tag.getInt("hp") : MAX_HP;
    }
}
