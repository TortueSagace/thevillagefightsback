package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A native rocket projectile -- a clone of JEG's rocket mechanism so the iron golem's rocket launcher no
 * longer needs JEG at runtime. It flies dead-straight (no gravity), deals a heavy direct hit, and detonates
 * on contact with a TNT-strength, block-breaking explosion that is attributed to its OWNER (the golem), so
 * the mod's ally-immunity in {@code VillageEvents.onLivingHurt} still spares villagers/golems caught in it.
 */
public class RocketEntity extends ThrowableProjectile implements ItemSupplier {
    public static final float DIRECT_DAMAGE = 28.0F;     // matches JEG's rocket projectile damage
    public static final float EXPLOSION_RADIUS = 5.0F;   // matches JEG's default missile blast
    public static final double SPEED = 2.5;              // blocks/tick: fast, near-straight flight
    private static final int MAX_LIFETIME = 200;         // self-destruct if it never hits anything (~10s)
    private static final int AIRSTRIKE_LIFETIME = 1200;  // air-strike drops fall a long way -> a generous cap (~60s)

    /** Air-strike drops fall at a CONSTANT speed (re-asserted each tick to defeat the 0.99 air-drag that would
     *  otherwise decelerate them to a crawl and strand them above the target) and never despawn early mid-fall. */
    private boolean airStrike;

    /**
     * The ONE shared rocket attack: launch a rocket from {@code shooter} straight at {@code target}'s centre,
     * with the launch sound. Both the gigachad golems and the stage-4 wall defender fire through here, so any
     * future change to the launch (muzzle effect, animation hook, sound) automatically applies to both.
     */
    public static void fire(net.minecraft.world.entity.Mob shooter, LivingEntity target) {
        if (shooter.level().isClientSide()) return;
        RocketEntity rocket = new RocketEntity(shooter.level(), shooter);
        net.minecraft.world.phys.Vec3 from = rocket.position();
        net.minecraft.world.phys.Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize();
        rocket.setDeltaMovement(dir.scale(SPEED));
        rocket.setYRot((float) (Math.atan2(dir.x, dir.z) * (180.0 / Math.PI)));
        rocket.setXRot((float) (Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI)));
        shooter.level().addFreshEntity(rocket);
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    /** Player-style fire: launch a rocket along the shooter's look direction (no locked target). */
    public static void fireFromLook(LivingEntity shooter) {
        if (shooter.level().isClientSide()) return;
        RocketEntity rocket = new RocketEntity(shooter.level(), shooter);
        net.minecraft.world.phys.Vec3 look = shooter.getViewVector(1.0F);
        rocket.setDeltaMovement(look.scale(SPEED));
        rocket.setYRot(shooter.getYRot());
        rocket.setXRot(shooter.getXRot());
        shooter.level().addFreshEntity(rocket);
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, net.minecraft.sounds.SoundSource.PLAYERS, 1.4F, 0.8F);
    }

    /**
     * Air-strike drop: a rocket that spawns high above ({@code x},{@code z}) and falls dead-straight DOWN, with
     * the exact same heavy hit + blast as the gigachad golem's rocket. No owner -- the air-strike code
     * pre-clears the impact zone of village allies, so ally-immunity attribution isn't needed.
     */
    public static void dropFromSky(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        RocketEntity rocket = new RocketEntity(ModEntities.ROCKET.get(), level);
        rocket.airStrike = true;
        rocket.setPos(x, y, z);
        rocket.xOld = x; rocket.yOld = y; rocket.zOld = z;
        rocket.setXRot(90.0F); // nose-down
        rocket.setYRot(0.0F);
        rocket.setDeltaMovement(0.0, -SPEED, 0.0);
        level.addFreshEntity(rocket);
        level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.7F);
    }

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public RocketEntity(Level level, LivingEntity shooter) {
        super(ModEntities.ROCKET.get(), shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        // No synced data: it's always a rocket and its velocity rides the spawn packet.
    }

    /** Dead-straight flight (the golem aims directly at the target). */
    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        long _p = ModProfiler.now();
        // Air-strike drops fall at a constant speed: re-assert the downward velocity BEFORE the move so the
        // 0.99 air-drag in ThrowableProjectile#tick can't decelerate them and strand them above the target.
        if (airStrike && !level().isClientSide()) setDeltaMovement(0.0, -SPEED, 0.0);
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            // A bright, continuous fiery beam along this tick's flight path. We space the particles a fixed
            // fraction of a block apart (not a fixed step count) so the streak stays solid no matter how fast
            // the rocket moves: a tight FLAME core for the glowing beam, plus SMOKE for the contrail.
            net.minecraft.world.phys.Vec3 now = position();
            net.minecraft.world.phys.Vec3 prev = new net.minecraft.world.phys.Vec3(xOld, yOld, zOld);
            double dist = now.subtract(prev).length();
            int steps = Math.max(1, (int) Math.ceil(dist / 0.15)); // ~6-7 emit points per block -> solid line
            for (int i = 1; i <= steps; i++) {
                net.minecraft.world.phys.Vec3 p = prev.lerp(now, (double) i / steps);
                // FLAME core: a few per point for thickness, FORCED so the glowing beam reads from far away.
                for (net.minecraft.server.level.ServerPlayer pl : sl.players()) {
                    sl.sendParticles(pl, net.minecraft.core.particles.ParticleTypes.FLAME, true, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
                }
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            // Firework sparks at the rocket head, forced so the projectile itself is visible from far.
            for (net.minecraft.server.level.ServerPlayer pl : sl.players()) {
                sl.sendParticles(pl, net.minecraft.core.particles.ParticleTypes.FIREWORK, true, now.x, now.y, now.z, 2, 0.05, 0.05, 0.05, 0.02);
            }
        }
        if (!level().isClientSide() && tickCount > (airStrike ? AIRSTRIKE_LIFETIME : MAX_LIFETIME)) {
            discard();
        }
        ModProfiler.add("rocket", _p);
    }

    /** Direct impact: a heavy hit on the struck entity, attributed to the golem (owner). */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide()) return;
        Entity owner = getOwner();
        result.getEntity().hurt(
                damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null),
                DIRECT_DAMAGE);
        result.getEntity().setSecondsOnFire(5); // the blast sets the struck unit alight regardless of the block-fire config
    }

    /** Any contact (block or entity): detonate with JEG's explosion animation, then vanish. */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        Entity owner = getOwner();
        // Same damage + block-breaking as a TNT blast (mobGriefing-gated), but built by hand so we DON'T send
        // the vanilla explosion packet -- that lets us show JEG's big-explosion animation instead of the
        // generic TNT poof. explode() applies entity damage + knockback; finalizeExplosion(false) destroys
        // blocks with NO vanilla particles.
        // Block destruction + fire are independently config-gated (per rocket source); the blast ALWAYS damages
        // and ignites entities via ex.explode() regardless. Air-strike rockets read the air_strike_* switches;
        // every other creature/unit rocket reads the creature_rocket_* switches.
        boolean grief = sl.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        boolean destroyBlocks = (airStrike ? Config.airStrikeDestroysBlocks() : Config.creatureRocketDestroysBlocks()) && grief;
        boolean setFire = airStrike ? Config.airStrikeSetsFire() : Config.creatureRocketSetsFire();
        net.minecraft.world.level.Explosion ex = new net.minecraft.world.level.Explosion(sl, owner, null, null,
                getX(), getY(), getZ(), EXPLOSION_RADIUS, false,
                destroyBlocks ? net.minecraft.world.level.Explosion.BlockInteraction.DESTROY_WITH_DECAY
                      : net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
        ex.explode();
        ex.finalizeExplosion(false);
        // Engine fire only ignites blocks it DESTROYS, so scatter our own fire when the fire switch is on
        // (independent of destruction). Units are already set alight by the blast above.
        if (setFire) ModExplosions.scorch(sl, blockPosition(), 3);
        // JEG's big-explosion animation + a smoke cloud + the usual blast sound. The emerald gigachad's rockets
        // detonate GREEN; everyone else's stay orange.
        net.minecraft.core.particles.SimpleParticleType blast = owner instanceof EmeraldGigachadGolemEntity
                ? ModParticles.GIGACHAD_EXPLOSION_GREEN.get() : ModParticles.GIGACHAD_EXPLOSION.get();
        sl.sendParticles(blast, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 10, 1.2, 1.2, 1.2, 0.02);
        sl.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F,
                (1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F);
        discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.ROCKET.get()); // the billboard the client renders
    }

}
