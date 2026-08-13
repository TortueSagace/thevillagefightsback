package com.example.villagefightsback;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The wall defender: an immobile conduit-like turret creature. 100 HP (native LivingEntity health, so it
 * takes damage from arrows/explosions normally -- no custom handling), fires a real JEG gun once per second
 * at the nearest hostile (the same {@code Enemy} notion the villagers/guards use), dealing ~10 damage. It
 * never moves and ignores gravity/knockback, so it stays centred in its protecting-glass shell. Line of
 * sight is checked from just outside the shell so it can see (and shoot) through its own glass.
 */
public class WallDefenderEntity extends Mob {
    private static final double RANGE = 24.0;
    private static final int FIRE_INTERVAL = 20;     // one bullet per second (pre-stage-4 revolver)
    private static final int RETARGET_INTERVAL = 10; // re-scan for a target only every 0.5s (the heavy part)
    private static final float REVOLVER_DAMAGE = 10.0F; // jeg revolver base 5 + our +5 bonus = ~10 per shot

    // Stage 4: the defender fires our native rocket (the same RocketEntity the iron golems use), NOT a JEG
    // bullet. Rockets only fly beyond MIN_ROCKET_RANGE so the radius-5 blast can never reach our own glass
    // shell; a target closer than that is plinked with the revolver instead (so a point-blank attacker is
    // still engaged without the turret blowing itself up).
    private static final int ROCKET_INTERVAL = 40;    // one rocket per 2s at stage 4 (very powerful)
    private static final double MIN_ROCKET_RANGE = 8.0; // > blast radius (+ shell clearance) -> never self-blast

    // Synced to the client so the renderer can pick the stage-4 texture.
    private static final EntityDataAccessor<Boolean> DATA_STAGE4 =
            SynchedEntityData.defineId(WallDefenderEntity.class, EntityDataSerializers.BOOLEAN);

    private final boolean forcedStage4; // true for the stage-4 spawn-egg variant (always rocket mode)
    private boolean gunOnly;            // lure-tower defender: NEVER rockets, even at stage 4 (would wreck the powder-snow trap)
    private int fireCooldown;
    private int retargetCooldown;
    private boolean rocketMode;   // our village is at stage 4 -> fire rockets (refreshed on each re-target)
    private LivingEntity cachedTarget;

    public WallDefenderEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        this.forcedStage4 = type == ModEntities.WALL_DEFENDER_STAGE4.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STAGE4, false);
    }

    /** Client-side: true if this defender should render as a stage-4 (rocket) defender. The forced stage-4
     *  type (spawn egg + the upgrade-tree GUI dummy) is gold immediately; village defenders flip the synced
     *  flag once their nearest brain node is stage 4. */
    public boolean isStage4Visual() {
        return this.forcedStage4 || this.entityData.get(DATA_STAGE4);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        // No goals: it's a stationary turret driven entirely by aiStep().
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (fireCooldown > 0) fireCooldown--;
        if (retargetCooldown > 0) retargetCooldown--;

        // Re-acquire (the heavy entity scan + per-candidate LOS raytraces) only every RETARGET_INTERVAL
        // ticks, or as soon as the cached target dies/leaves range -- NOT every tick. Aiming at the cached
        // target stays per-tick because that's just a cheap rotation.
        if (retargetCooldown <= 0 || !isValidTarget(cachedTarget)) {
            cachedTarget = acquireTarget();
            rocketMode = isStage4();                   // refresh the stage gate alongside the (throttled) re-scan
            this.entityData.set(DATA_STAGE4, rocketMode); // sync to the client for the stage-4 texture
            retargetCooldown = RETARGET_INTERVAL;
        }
        setTarget(cachedTarget);
        if (cachedTarget == null) return;
        CombatUtil.aimAt(this, cachedTarget);            // face the target (the rocket/bullet is aimed by hand)
        if (fireCooldown > 0) return;

        if (rocketMode) {
            // Stage 4: the EXACT same rocket attack as the gigachad golems (RocketEntity.fire). A point-blank
            // target inside the safe blast range gets the revolver instead, so the immobile turret never blasts
            // its own shell. Either way the powerful turret fires only every ROCKET_INTERVAL (2s).
            if (distanceToSqr(cachedTarget) >= MIN_ROCKET_RANGE * MIN_ROCKET_RANGE) {
                RocketEntity.fire(this, cachedTarget);
            } else {
                BulletEntity.fire(this, cachedTarget, REVOLVER_DAMAGE);
            }
            fireCooldown = ROCKET_INTERVAL;
        } else {
            BulletEntity.fire(this, cachedTarget, REVOLVER_DAMAGE); // pre-stage-4: revolver, once per second
            fireCooldown = FIRE_INTERVAL;
        }
    }

    /** Mark this defender gun-only (never rockets). Used for the lure-tower defender so its blasts can't
     *  destroy the powder-snow trap below it, even after the village reaches stage 4. */
    public void setGunOnly(boolean v) {
        this.gunOnly = v;
    }

    /** True when this defender fires rockets: forced (stage-4 spawn egg) or its nearest brainNode is stage 4.
     *  A gun-only defender (lure tower) never rockets, regardless of village stage. */
    private boolean isStage4() {
        if (gunOnly) return false;
        if (forcedStage4) return true;
        if (!(level() instanceof ServerLevel sl)) return false;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        GlobalPos nearest = null;
        double best = Double.MAX_VALUE;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            double d = gp.pos().distSqr(blockPosition());
            if (d < best) { best = d; nearest = gp; }
        }
        return nearest != null && data.econ(nearest).getLevel() >= 4;
    }

    /** Cheap per-tick check (no raytrace) so we can keep a target between full re-acquisitions. */
    private boolean isValidTarget(LivingEntity t) {
        return t != null && t.isAlive() && distanceToSqr(t) <= RANGE * RANGE;
    }

    private LivingEntity acquireTarget() {
        Vec3 eye = getEyePosition();
        // A player who attacked the village (or this defender) is the priority target -- same as the
        // villagers/guards (the brain node's anti-grief aggro reaches us now that we're an "ally").
        net.minecraft.world.entity.player.Player aggro = VillageEvents.getAggroPlayer(this);
        if (aggro != null && aggro.isAlive() && distanceToSqr(aggro) <= RANGE * RANGE
                && canShootThroughShell(eye, aggro)) {
            return aggro;
        }
        AABB box = getBoundingBox().inflate(RANGE);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                x -> com.example.villagefightsback.Factions.isVillageThreat(x)
                        && !com.example.villagefightsback.Factions.isSparedByVillage(x) // never Darius or the friendly Vindictive Village Spirit
                        && x.isAlive() && x != this)) {
            double d = distanceToSqr(e);
            if (d > RANGE * RANGE || d >= bestD) continue;
            if (!canShootThroughShell(eye, e)) continue;
            best = e;
            bestD = d;
        }
        return best;
    }

    /** LOS that ignores our own shell by starting ~2 blocks out (past the 1-thick protecting glass). */
    private boolean canShootThroughShell(Vec3 from, LivingEntity t) {
        Vec3 te = t.getEyePosition();
        Vec3 dir = te.subtract(from);
        double len = dir.length();
        if (len < 2.0) return true;
        Vec3 start = from.add(dir.scale(2.0 / len));
        return level().clip(new ClipContext(start, te,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("GunOnly", gunOnly);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.gunOnly = tag.getBoolean("GunOnly");
    }

    // --- immobile turret: never translate, never get pushed or knocked back ---
    @Override public void travel(Vec3 wanted) { /* anchored */ }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
}
