package com.example.villagefightsback;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The "Village Brain Node, Unleashed": a green Warden-bodied boss with the brain node block for a head (it
 * reuses {@code BrainNodeWardenRenderer} via {@link BrainNodeWardenEntity}). It is summoned when a village's
 * brainNode block is destroyed by anything other than silk touch (see {@link UnleashedManager}). It emerges
 * from the ground, then hunts every player and monster at very long range (no line of sight needed), with
 * four attacks. It absorbs 90% of explosion damage, takes no fall damage, and is immune to fire/effects like
 * an Emeraldger. It ends when it is killed, or when it has slain 64 creatures -- in which case it reverts to
 * a fresh stage-1 brainNode block and its vindictive guards become stage-3 villagers.
 *
 * AI design: we KEEP the inherited Warden brain (so its memories stay registered and Warden#hurt /
 * #doHurtTarget never throw) but we NEVER tick it (customServerAiStep does not call super), and drive
 * movement / melee / the four attacks / the emerge timer ourselves. The Warden sonic boom therefore never
 * fires (its behaviour never runs).
 */
public class UnleashedBrainNodeEntity extends BrainNodeWardenEntity {
    private static final String NBT_KILLS = "vgun_unleashed_kills";
    private static final String NBT_EMERGE = "vgun_unleashed_emerge";

    public static final int KILLS_TO_WIN = 64;
    private static final int EMERGE_TICKS = 134;
    private static final double DETECT_RANGE = 64.0;   // "same long range it saw villagers"
    private static final double MELEE_REACH_SQR = 9.0; // 3 blocks
    private static final int MELEE_CD = 10;             // 0.5s
    private static final int GREEN_WAVE_CD = 600;       // 30s -- the favourite
    private static final int JUMP_CD = 240;             // 12s
    private static final int CIRCULAR_CD = 360;         // 18s
    private static final double WALK_SPEED = 1.25;
    private static final int MIN_JUMP_HEADROOM = 6;
    private static final double JUMP_VELOCITY = 1.2;    // ~8 blocks up
    private static final double PROPEL_VELOCITY = 1.2;
    private static final int CIRCULAR_STEPS = 24;       // 360 / 15 degrees

    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.literal("Vindictive Village Spirit"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private static final int BOSS_HEARTBEAT_INTERVAL = 16; // fast Warden pulse (calm warden is ~40t; this is "angry")
    private int heartbeatTimer;

    private int killCount;
    private int emergeTicks = EMERGE_TICKS;
    private int glowTicks;              // glow for ~3s after spawning, then stop (0 = not glowing)
    private boolean reverted;           // true once the 64-kill win has fired (guards against double-revert)
    private int golemSummonTimer;       // ticks until a jump's vindictive gigachad spawns (0 = none pending)
    private BlockPos golemSummonPos;    // where that gigachad spawns (the jump's takeoff spot)

    private static final int GOLEM_SUMMON_DELAY = 12; // 0.6s after a jump -> a vindictive gigachad rises
    private static final int MAX_GIGACHADS = 5;       // cap on jump-summoned gigachads alive in the boss's reach
    @Nullable private LivingEntity currentTarget;
    private int greenWaveCd, jumpCd, circularCd, meleeCd, retargetCd, teleportCd;
    private int repathCd; // throttle the (very expensive) Warden-sized pathfind: recompute at most ~2x/sec
    private boolean jumping;
    private int jumpWatch;
    private int spinTicks;
    private float spinAngle;
    private final Set<Integer> spinHit = new HashSet<>();

    public UnleashedBrainNodeEntity(EntityType<? extends Warden> type, Level level) {
        super(type, level);
        this.bossBar.setDarkenScreen(true);
        this.xpReward = 500; // a boss-sized XP reward
    }

    /** On death the boss yields its prize: the Village Brain Node block (you can re-found a village with it). */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
                                       boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(new net.minecraft.world.item.ItemStack(ModBlocks.VILLAGE_BRAIN_NODE_ITEM.get()));
        this.spawnAtLocation(new net.minecraft.world.item.ItemStack(ModBlocks.FERTILE_SCULK_CATALYST_ITEM.get()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Warden.createAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.4)        // sprinting-warden pace
                .add(Attributes.FOLLOW_RANGE, DETECT_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 20.0)                // equivalent to a full diamond armour set
                .add(Attributes.ARMOR_TOUGHNESS, 8.0);
    }

    // --- emerge on summon -----------------------------------------------------------------------
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor lvl, DifficultyInstance diff, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData r = super.finalizeSpawn(lvl, diff, reason, data);
        setPose(Pose.EMERGING);
        this.emergeTicks = EMERGE_TICKS;
        this.glowTicks = 60;          // glow for ~3s on spawn so the player can spot it
        playSound(SoundEvents.WARDEN_EMERGE, 5.0F, 0.8F);
        return r;
    }

    // Profile the boss's ENTIRE per-tick cost (vanilla Warden tick + our customServerAiStep below).
    @Override
    public void tick() {
        long _p = ModProfiler.now();
        super.tick();
        ModProfiler.add("boss.tick", _p);
    }

    // --- AI: drive everything ourselves; the Warden brain is registered but never ticked ---------
    @Override
    protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel sl)) return;
        // The unleashed boss pulses the Warden's heartbeat FAST (an angry warden), the whole time it's loose.
        if (--heartbeatTimer <= 0) { playSound(SoundEvents.WARDEN_HEARTBEAT, 4.0F, 1.0F); heartbeatTimer = BOSS_HEARTBEAT_INTERVAL; }
        if (glowTicks > 0) { setGlowingTag(true); if (--glowTicks == 0) setGlowingTag(false); } // ~3s spawn glow
        if (golemSummonTimer > 0 && --golemSummonTimer == 0) summonGigachad(sl); // a jump's delayed gigachad
        bossBar.setProgress(getHealth() / getMaxHealth());

        if (hasPose(Pose.EMERGING) || hasPose(Pose.DIGGING)) { // rise from the ground (invulnerable while doing so)
            if (--emergeTicks <= 0) setPose(Pose.STANDING);
            getNavigation().stop();
            return;
        }

        if (greenWaveCd > 0) greenWaveCd--;
        if (jumpCd > 0) jumpCd--;
        if (circularCd > 0) circularCd--;
        if (meleeCd > 0) meleeCd--;
        if (teleportCd > 0) teleportCd--;

        if ((tickCount & 1) == 0) breakBlocksAround(sl); // smash through walls; every 2 ticks is lossless
                                                         // (a block sits in the box >2 ticks at the boss's speed)

        if (spinTicks > 0) { tickCircular(sl); return; }
        if (jumping) { tickJump(sl); return; }

        if (--retargetCd <= 0) { currentTarget = pickTarget(sl, false); retargetCd = 20; } // re-scan ~1s (cheaper)
        LivingEntity t = currentTarget;
        if (t == null || !t.isAlive()) { getNavigation().stop(); return; }
        getLookControl().setLookAt(t, 30.0F, 30.0F);
        double distSqr = distanceToSqr(t);

        // Attack priority: green wave (favourite) -> jump AoE -> circular sweep -> approach + melee.
        if (greenWaveCd <= 0) { greenWave(sl); greenWaveCd = GREEN_WAVE_CD; return; }
        int near = countTargetsWithin(sl, 10.0);
        if (jumpCd <= 0 && near > 1 && hasJumpRoom(sl)) { startJump(t); jumpCd = JUMP_CD; return; }
        if (circularCd <= 0 && near >= 1) { startCircular(); circularCd = CIRCULAR_CD; return; }

        if (distSqr <= MELEE_REACH_SQR) {
            getNavigation().stop();
            if (meleeCd <= 0) { swing(InteractionHand.MAIN_HAND); doHurtTarget(t); meleeCd = MELEE_CD; }
        } else {
            // Re-path at most ~twice a second instead of every tick: a Warden-sized pathfind over the village
            // is hugely expensive and was ~3% of the whole server tick on its own. In between, keep following
            // the cached path (moving = path still in progress) so the teleport-fallback logic is unchanged.
            boolean moving;
            if (--repathCd <= 0) {
                moving = getNavigation().moveTo(t, WALK_SPEED);
                repathCd = 10;
            } else {
                moving = !getNavigation().isDone();
            }
            if ((!moving || distSqr > 576.0) && teleportCd <= 0) { teleportTowards(sl, t); teleportCd = 40; }
        }
    }

    // --- attack 1: green sculk wave (favourite, 30s): banish everyone it passes to Limbo, no damage ----
    private void greenWave(ServerLevel sl) {
        LivingEntity t = pickTarget(sl, true); // aim the highest-HP target in range
        if (t == null) return;
        Vec3 origin = position().add(0.0, 1.6, 0.0);
        Vec3 dir = t.getEyePosition().subtract(origin).normalize();
        double range = 100.0;
        double radius = 5.0;                        // twice the old 2.5 -- a fat banishing beam
        for (int i = 1; i < range + 7; i++) {       // the green warden sonic-boom rings, one per block
            Vec3 p = origin.add(dir.scale(i));
            sl.sendParticles(ModParticles.GREEN_SONIC_BOOM.get(), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.3F);
        Vec3 end = origin.add(dir.scale(range));
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(origin, end).inflate(radius), this::isHostileTarget)) {
            // Test the mob's WHOLE hitbox against the beam (not just its centre point) so big/tall mobs --
            // iron golems, ravagers, the boss-sized -- are caught instead of slipping through. contains(origin)
            // catches point-blank targets (clip() returns empty when the origin is already inside the box).
            net.minecraft.world.phys.AABB box = e.getBoundingBox().inflate(radius);
            if (box.contains(origin) || box.clip(origin, end).isPresent()) {
                LimboDimension.sendToLimbo(e); // no damage -- banished
                registerKill();               // a banished creature counts toward the 64-kill win
            }
        }
    }

    // --- attack 2: jump 8 up, slam down -> 20 dmg + Virus I + propel up, within 10 blocks (12s) --------
    private void startJump(LivingEntity t) {
        Vec3 to = t.position().subtract(position());
        double hl = Math.sqrt(to.x * to.x + to.z * to.z);
        double hx = hl > 1.0E-4 ? to.x / hl : 0, hz = hl > 1.0E-4 ? to.z / hl : 0;
        setDeltaMovement(hx * 0.5, JUMP_VELOCITY, hz * 0.5);
        hasImpulse = true;
        hurtMarked = true;
        fallDistance = 0.0F;
        jumping = true;
        jumpWatch = 0;
        golemSummonPos = blockPosition();          // a vindictive gigachad rises here ~0.6s later
        golemSummonTimer = GOLEM_SUMMON_DELAY;
        playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.6F);
    }

    private void tickJump(ServerLevel sl) {
        jumpWatch++;
        if (jumpWatch > 6 && onGround()) { jumpLand(sl); jumping = false; }
        else if (jumpWatch > 100) jumping = false; // safety
    }

    private void jumpLand(ServerLevel sl) {
        UnleashedManager.shakeNearby(sl, this, 0.9F, 14);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 0.2, getZ(), 8, 1.0, 0.2, 1.0, 0.0);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10.0), this::isHostileTarget)) {
            if (distanceToSqr(e) > 100.0) continue;
            e.hurt(damageSources().magic(), 18.0F); // armour-bypassing -> a clean 18
            EmeraldVirus.infect(e, 0);
            e.setDeltaMovement(e.getDeltaMovement().x, PROPEL_VELOCITY, e.getDeltaMovement().z);
            e.hasImpulse = true;
            e.hurtMarked = true;
        }
    }

    // --- attack 3: 360 degree sweep (18s): a normal green wave each 15 degrees, dmg + Virus I, no limbo --
    private void startCircular() {
        spinTicks = CIRCULAR_STEPS;
        spinAngle = getYRot();
        spinHit.clear();
        playSound(SoundEvents.WARDEN_ROAR, 3.0F, 1.2F);
    }

    private void tickCircular(ServerLevel sl) {
        double rad = Math.toRadians(spinAngle);
        Vec3 dir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
        Vec3 origin = position().add(0.0, 1.0, 0.0);
        double range = 24.0;
        for (int i = 1; i < range; i++) {
            Vec3 p = origin.add(dir.scale(i));
            sl.sendParticles(ModParticles.GREEN_SONIC_BOOM.get(), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin.add(dir.scale(range))).inflate(1.5), this::isHostileTarget)) {
            if (!spinHit.add(e.getId())) continue; // hit each target at most once per sweep
            Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            double along = c.subtract(origin).dot(dir);
            if (along < 0 || along > range) continue;
            if (origin.add(dir.scale(along)).distanceTo(c) > 1.6) continue;
            e.hurt(damageSources().sonicBoom(this), 10.0F); // normal sculk-wave damage
            EmeraldVirus.infect(e, 0);
        }
        spinAngle += 15.0F;
        setYRot(spinAngle);
        setYBodyRot(spinAngle);
        if (--spinTicks <= 0) setYHeadRot(spinAngle);
    }

    // --- melee (attack 4): warden-strength hit that also lands the Alien-Slasher (stage-3) payload -------
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean r = super.doHurtTarget(target); // 30 dmg + warden knockback (writes a registered memory: safe)
        if (r && target instanceof LivingEntity le) {
            EmeraldVirus.infect(le, 0); // Virus I (stage-3 equivalent)
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, true)); // Slowness IV
            le.igniteForSeconds(5);
            applyBigKnockback(le);
        }
        return r;
    }

    private void applyBigKnockback(LivingEntity target) {
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) { dx = -Math.sin(getYRot() * ((float) Math.PI / 180F)); dz = Math.cos(getYRot() * ((float) Math.PI / 180F)); len = 1.0; }
        target.setDeltaMovement(dx / len * 2.6, 1.0, dz / len * 2.6);
        target.hasImpulse = true;
        target.hurtMarked = true;
    }

    // --- movement helpers -----------------------------------------------------------------------
    private void teleportTowards(ServerLevel sl, LivingEntity t) {
        for (int i = 0; i < 10; i++) {
            double tx = t.getX() + (random.nextDouble() - 0.5) * 5.0;
            double ty = t.getY();
            double tz = t.getZ() + (random.nextDouble() - 0.5) * 5.0;
            if (randomTeleport(tx, ty, tz, true)) {
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1.0F, 0.7F);
                return;
            }
        }
    }

    private void breakBlocksAround(ServerLevel sl) {
        long _p = ModProfiler.now();
        AABB box = getBoundingBox().inflate(0.15);
        for (BlockPos p : BlockPos.betweenClosed(
                (int) Math.floor(box.minX), (int) Math.floor(box.minY + 0.2), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))) {
            BlockState s = sl.getBlockState(p);
            if (s.isAir()) continue;
            if (s.getBlock().getExplosionResistance() >= 1200.0F) continue; // obsidian-tough or harder: spared
            if (s.getDestroySpeed(sl, p) < 0) continue;                      // unbreakable (bedrock)
            sl.destroyBlock(p, false);
        }
        ModProfiler.add("boss.breakBlocks", _p);
    }

    private boolean hasJumpRoom(ServerLevel sl) {
        BlockPos head = blockPosition().above((int) Math.ceil(getBbHeight()));
        for (int i = 0; i < MIN_JUMP_HEADROOM; i++) {
            if (!sl.getBlockState(head.above(i)).isAir()) return false;
        }
        return true;
    }

    // --- targeting ------------------------------------------------------------------------------
    @Nullable
    private LivingEntity pickTarget(ServerLevel sl, boolean highestHp) {
        LivingEntity best = null;
        double bestKey = highestHp ? -1.0 : Double.MAX_VALUE;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DETECT_RANGE), this::isHostileTarget)) {
            if (highestHp) { if (e.getHealth() > bestKey) { bestKey = e.getHealth(); best = e; } }
            else { double d = distanceToSqr(e); if (d < bestKey) { bestKey = d; best = e; } }
        }
        return best;
    }

    private int countTargetsWithin(ServerLevel sl, double radius) {
        return sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::isHostileTarget).size();
    }

    /** Hostile to every player (non-creative) and every monster, except the infected faction and General
     *  Darius -- the Spirit is friendly toward the village (Darius included), so it never fights him. */
    private boolean isHostileTarget(LivingEntity e) {
        if (e == this || !e.isAlive() || Factions.isInfected(e) || Factions.isVillageChampion(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof Enemy;
    }

    // --- kill -> vindictive guard + 64-kill win -------------------------------------------------
    private static final int MAX_GUARDS = 20; // cap simultaneous vindictive guards in the boss's reach

    /** A small sculk-soul puff marking a vindictive unit snapping back to normal on the spirit's revert. */
    private static void revertPuff(ServerLevel sl, BlockPos p) {
        sl.sendParticles(ParticleTypes.SCULK_SOUL, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 18, 0.4, 0.6, 0.4, 0.02);
        sl.playSound(null, p, SoundEvents.SCULK_CATALYST_BLOOM, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.4F);
    }

    /** True if there is room for another vindictive guard (fewer than {@value #MAX_GUARDS} within the boss's
     *  reach). When false, a killed creature just dies normally instead of converting. */
    public boolean canMakeGuard() {
        if (!(level() instanceof ServerLevel sl)) return false;
        return sl.getEntitiesOfClass(VindictiveGuardEntity.class, getBoundingBox().inflate(DETECT_RANGE)).size() < MAX_GUARDS;
    }

    /** Called from {@link UnleashedManager} when a creature dies to a DIRECT hit from this boss. */
    public void onDirectKill(LivingEntity victim) {
        if (!(level() instanceof ServerLevel sl)) return;
        spawnVindictiveGuard(sl, victim.blockPosition());
        registerKill();
    }

    /** Count ONE creature toward the 64-kill win (update the bar; win at the cap). Every way the boss
     *  removes a creature funnels through here: direct-hit conversion, a Limbo banish, or -- when the
     *  20-guard cap is full -- a creature that just dies. Guarded so the win fires exactly once. */
    public void registerKill() {
        if (reverted) return;
        killCount++;
        bossBar.setName(Component.literal("Vindictive Village Spirit  (" + killCount + "/" + KILLS_TO_WIN + ")"));
        if (killCount >= KILLS_TO_WIN && level() instanceof ServerLevel sl) { reverted = true; winAndRevert(sl); }
    }

    public int getKillCount() { return killCount; }

    private void spawnVindictiveGuard(ServerLevel sl, BlockPos pos) {
        VindictiveGuardEntity g = ModEntities.VINDICTIVE_GUARD.get().create(sl);
        if (g == null) return;
        g.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        g.finalizeSpawn(sl, sl.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
        g.setBaby(false);
        sl.addFreshEntity(g);
        // Make the "killed -> vindictive guard" transformation clearly visible.
        sl.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 24, 0.4, 0.6, 0.4, 0.02);
        sl.sendParticles(ParticleTypes.SCULK_CHARGE_POP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.4, 0.6, 0.4, 0.0);
        sl.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, getSoundSource(), 1.0F, 1.3F);
    }

    /** Summon a vindictive gigachad at the last jump's takeoff spot, capped at {@value #MAX_GIGACHADS} in reach. */
    private void summonGigachad(ServerLevel sl) {
        BlockPos at = golemSummonPos;
        golemSummonPos = null;
        if (at == null || reverted) return;
        if (sl.getEntitiesOfClass(VindictiveGigachadEntity.class, getBoundingBox().inflate(DETECT_RANGE)).size() >= MAX_GIGACHADS) return;
        VindictiveGigachadEntity g = ModEntities.VINDICTIVE_GIGACHAD.get().create(sl);
        if (g == null) return;
        g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        g.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        sl.addFreshEntity(g);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 24, 0.5, 0.8, 0.5, 0.02);
        sl.playSound(null, at, SoundEvents.SCULK_CATALYST_BLOOM, getSoundSource(), 1.2F, 0.7F);
    }

    private void winAndRevert(ServerLevel sl) {
        BlockPos at = blockPosition();
        GlobalPos gp = GlobalPos.of(sl.dimension(), at);
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        data.eraseVillage(gp); // wipe the saved per-village state so it's a true stage-1 rebirth
        // Also drop the TRANSIENT wall-build state (reservations / skips / abandon counts) at this spot, so a
        // wall rebuilt here can't inherit "abandoned >3 times" cells from the previous village and stall.
        com.example.villagefightsback.wall.WallBuild.clearPlan(gp);
        // Place the node with a normal block-update: this fires VillageBrainNodeBlock#onPlace -> data.addBlock,
        // EXACTLY as if a player had placed the brain node block here (fresh stage-1 village, discovery banner
        // when a player is near, walls/defenders/economy all rebuild off data.blocks()).
        sl.setBlock(at, ModBlocks.VILLAGE_BRAIN_NODE.get().defaultBlockState(), 3);

        // EVERY vindictive unit in the dimension reverts to its village variant -- handled by the removal hook
        // (remove() below) the moment this spirit is discarded, so the win path and a normal death behave the same.
        sl.players().forEach(pl -> pl.displayClientMessage(
                Component.literal("§5The Village Brain Node is reborn. The village rises again."), false));
        bossBar.removeAllPlayers();
        discard(); // -> remove(DISCARDED) -> revertAllVindictive(sl)
    }

    private static final net.minecraft.resources.ResourceLocation GUARD_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");

    /**
     * Revert EVERY vindictive unit currently loaded in {@code sl} to its village variant: vindictive guards
     * become stage-3 guards, vindictive gigachads become gigachad golems. Called the instant the LAST Vindictive
     * Village Spirit leaves the dimension (death, /kill, or the win-revert), since the faction's hold is broken.
     */
    public static void revertAllVindictive(ServerLevel sl) {
        java.util.List<VindictiveGuardEntity> guards = new java.util.ArrayList<>();
        java.util.List<VindictiveGigachadEntity> gigas = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : sl.getAllEntities()) {
            if (e instanceof VindictiveGuardEntity vg) guards.add(vg);
            else if (e instanceof VindictiveGigachadEntity vgc) gigas.add(vgc);
        }
        // entityTypeOrNull, not ENTITY_TYPE.get: the entity registry is DEFAULTED, so get() would return a PIG
        // rather than null when Guard Villagers is absent (see MCACompat#present).
        net.minecraft.world.entity.EntityType<?> guardType =
                com.example.villagefightsback.compat.MCACompat.entityTypeOrNull(GUARD_ID);
        for (VindictiveGuardEntity vg : guards) {
            BlockPos p = vg.blockPosition();
            float yaw = vg.getYRot();
            vg.discard();
            if (guardType == null) continue; // Guard Villagers not installed -> the vindictive guard just leaves
            if (!(guardType.create(sl) instanceof net.minecraft.world.entity.Mob guard)) continue;
            guard.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0.0F);
            UpgradeState.setUpgraded(guard); // stage 3 (armed by VillageEvents#onEntityJoin)
            guard.finalizeSpawn(sl, sl.getCurrentDifficultyAt(p), MobSpawnType.MOB_SUMMONED, null);
            sl.addFreshEntity(guard);
            revertPuff(sl, p);
        }
        for (VindictiveGigachadEntity vgc : gigas) {
            BlockPos p = vgc.blockPosition();
            float yaw = vgc.getYRot();
            vgc.discard();
            GigachadGolemEntity g = ModEntities.GIGACHAD_GOLEM.get().create(sl);
            if (g == null) continue;
            g.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0.0F);
            g.finalizeSpawn(sl, sl.getCurrentDifficultyAt(p), MobSpawnType.MOB_SUMMONED, null);
            sl.addFreshEntity(g);
            revertPuff(sl, p);
        }
    }

    /** True when no OTHER Vindictive Village Spirit is still alive in {@code sl} (so this one was the last). */
    private boolean isLastSpiritIn(ServerLevel sl) {
        for (net.minecraft.world.entity.Entity e : sl.getAllEntities()) {
            if (e != this && e instanceof UnleashedBrainNodeEntity other && other.isAlive() && !other.isRemoved()) {
                return false;
            }
        }
        return true;
    }

    // --- immunity (like an Emeraldger) + no fall damage -----------------------------------------
    @Override public boolean fireImmune() { return true; }
    @Override public void igniteForTicks(int ticks) { /* immune */ }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; } // incl. the Emerald Virus
    @Override public boolean canFreeze() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }

    // --- boss bar player tracking ---------------------------------------------------------------
    @Override
    public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        // The instant the LAST spirit truly leaves the dimension (killed / discarded / win-revert -- NOT a chunk
        // unload or dimension change), its whole vindictive faction reverts to village units.
        if (reason.shouldDestroy() && level() instanceof ServerLevel sl && isLastSpiritIn(sl)) {
            revertAllVindictive(sl);
        }
        super.remove(reason);
    }

    // --- persistence ----------------------------------------------------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(NBT_KILLS, killCount);
        tag.putInt(NBT_EMERGE, emergeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        killCount = tag.getInt(NBT_KILLS);
        if (tag.contains(NBT_EMERGE)) emergeTicks = tag.getInt(NBT_EMERGE);
        bossBar.setName(Component.literal("Vindictive Village Spirit  (" + killCount + "/" + KILLS_TO_WIN + ")"));
    }
}
