package com.example.villagefightsback;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.example.villagefightsback.goal.GeneralDariusGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * General Darius: a boss-grade upgrade of the stage-4 guard, summoned only by a stage-4 Village Brain Node's
 * emerald-funded reflex ({@link BrainReflexes}). Modelled on {@link VindictiveGuardEntity} (a custom
 * ZombieVillager-bodied fighter), but he wields the {@link GuardWeapons#alienSmasher() Alien Smasher} (no
 * crossbow), wears the FULL Limbo set including the Limbo Elytra Chestplate, and gets every Limbo full-set
 * advantage (fire/fall immunity, harmful-effect clearing, contact soft-block breaking, and -- below 50% HP --
 * the Limbo banish beam). He is immune to Limbo banishment (see {@link LimboDimension#sendToLimbo}), has a boss
 * bar, and a persistent "Darius" name tag. His combat brain is {@link GeneralDariusGoal}; his navigation is the
 * {@link DariusNavigation} (routes around village walls, plows through generic terrain).
 *
 * On the locked-target queue dying out, Darius walks back to his home brain node and despawns, REFUNDING the
 * 64 gems he cost to that brain's economy ({@link #refundAndDespawn()}).
 */
public class GeneralDariusEntity extends Monster {
    private static final String NBT_HOME_DIM = "darius_home_dim";
    private static final String NBT_HOME_POS = "darius_home_pos";

    /** The brain node's influence radius -- the zone Darius hunts within (mirrors BrainReflexes.RADIUS). */
    public static final double BRAIN_RADIUS = 100.0;
    /** Targets weaker than this max-HP are ignored once the first lock dies (a "big threat" floor). */
    private static final double NEXT_TARGET_MIN_MAXHP = 50.0;
    /** A player is "dangerous" if they have recently killed several villagers/guards (see BrainReflexes). */
    public static final int DANGEROUS_PLAYER_KILLS = 3;

    private static final int BEAM_COOLDOWN = 600; // 30s, only fires below 50% HP

    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.literal("General Darius"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);

    /** Hired-guard squad (UUIDs); transient -- released on death/despawn. */
    private final Set<UUID> squad = new LinkedHashSet<>();

    @Nullable private GlobalPos homeBrain;
    private int beamCooldown;
    private boolean despawning; // guards refund-once

    public GeneralDariusEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Darius"));
        setCustomNameVisible(true);
        this.xpReward = 100;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)        // stronger than a stage-4 guard
                .add(Attributes.MOVEMENT_SPEED, 0.32)       // a bit faster than a stage-4 guard (0.28)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0); // full KB immunity (matches the Limbo material)
    }

    @Override
    protected void registerGoals() {
        // No vanilla zombie goals (no super call): Darius's combat brain only, plus float + idle look.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GeneralDariusGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // NOTE: no target selector -- the goal locks/retaliates by itself (HurtByTarget is handled in-goal).
    }

    /** Custom navigation: route around village walls, plow through generic terrain (broken on contact). */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new DariusNavigation(this, level);
    }

    /** Equip the FULL Limbo set (incl. the elytra chestplate) + the Alien Smasher; never drop the gear. */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor lvl,
            net.minecraft.world.DifficultyInstance diff, net.minecraft.world.entity.MobSpawnType reason,
            net.minecraft.world.entity.SpawnGroupData data, net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData r = super.finalizeSpawn(lvl, diff, reason, data, tag);
        equipLimbo(EquipmentSlot.HEAD, ModArmor.LIMBO_HELMET.get());
        equipLimbo(EquipmentSlot.CHEST, ModArmor.LIMBO_ELYTRA_CHESTPLATE.get());
        equipLimbo(EquipmentSlot.LEGS, ModArmor.LIMBO_LEGGINGS.get());
        equipLimbo(EquipmentSlot.FEET, ModArmor.LIMBO_BOOTS.get());
        setItemSlot(EquipmentSlot.MAINHAND, GuardWeapons.alienSmasher().copy());
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        return r;
    }

    private void equipLimbo(EquipmentSlot slot, net.minecraft.world.item.Item piece) {
        setItemSlot(slot, new net.minecraft.world.item.ItemStack(piece));
        setDropChance(slot, 0.0F);
    }

    /** On death Darius always drops the actual (used) Alien Smasher he wielded -- enchants + durability intact.
     *  The limbo armour never drops (drop chances are 0). Only fires on a real death, not the refund-despawn. */
    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        net.minecraft.world.item.ItemStack smasher = getMainHandItem();
        if (!smasher.isEmpty()) {
            spawnAtLocation(smasher.copy());
            setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY); // no duplicate
        }
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random,
            net.minecraft.world.DifficultyInstance difficulty) {
        // intentionally empty: gear is set in finalizeSpawn
    }

    // --- the home brain node (for refund + walk-home) -------------------------------------------------
    public void setHomeBrain(@Nullable GlobalPos gp) { this.homeBrain = gp; }

    @Nullable
    public GlobalPos getHomeBrain() { return homeBrain; }

    /** The home brain block position if it is in THIS level, else null (used by the walk-home goal). */
    @Nullable
    public BlockPos getHomeBrainPos() {
        if (homeBrain == null) return null;
        if (level().dimension() != homeBrain.dimension()) return null;
        return homeBrain.pos();
    }

    // --- per-tick: Limbo full-set advantages + boss bar + squad + beam cooldown ----------------------
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;

        // Full-set Limbo advantages (LimboGearEvents only handles players, so apply them here to Darius).
        clearFire();
        fallDistance = 0.0F;
        if ((tickCount & 7) == 0) clearHarmfulEffects();
        LimboGear.breakSoftBlocksOnContact(this); // breaks soft blocks (incl. cobblestone) on contact

        if (beamCooldown > 0) beamCooldown--;

        bossBar.setProgress(getHealth() / getMaxHealth());
        // While hunting (a locked target) Darius glows so players can always spot him; off when walking home.
        setGlowingTag(getTarget() != null);

        if ((tickCount % 10) == 0) DariusSquad.tick(sl, this, squad); // hire + command the guard squad (count hidden)
    }

    private void clearHarmfulEffects() {
        var harmful = getActiveEffects().stream()
                .map(net.minecraft.world.effect.MobEffectInstance::getEffect)
                .filter(eff -> eff.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                .toList();
        for (var eff : harmful) removeEffect(eff);
    }

    // --- full-set damage immunity (fire + fall), mirroring LimboGearEvents.onHurt for the player --------
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return false; // fire + fall immunity
        }
        return super.hurt(source, amount);
    }

    @Override public boolean fireImmune() { return true; }
    @Override public void setSecondsOnFire(int seconds) { /* fire-immune */ }
    @Override public boolean causeFallDamage(float distance, float multiplier,
            net.minecraft.world.damagesource.DamageSource source) { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }

    // --- targeting: pick the biggest threat in the brain's range ------------------------------------
    /**
     * The enemy with the MOST max-health within {@link #BRAIN_RADIUS}, OR a "dangerous" enemy player (one who
     * has recently killed >= {@value #DANGEROUS_PLAYER_KILLS} villagers/guards near the home brain). When the
     * first lock has died, callers want the NEXT biggest -- so we only consider enemies with maxHealth
     * >= {@value #NEXT_TARGET_MIN_MAXHP}, plus dangerous players. Returns null when nothing qualifies.
     */
    @Nullable
    public LivingEntity pickBiggestThreat() {
        if (!(level() instanceof ServerLevel sl)) return null;
        LivingEntity best = null;
        double bestHp = -1.0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(BRAIN_RADIUS), this::isHuntable)) {
            boolean dangerous = e instanceof Player p && BrainReflexes.isDangerousPlayer(sl, p, homeBrain);
            if (!dangerous && e.getMaxHealth() < NEXT_TARGET_MIN_MAXHP) continue;
            double hp = dangerous ? Math.max(e.getMaxHealth(), 1000.0) : e.getMaxHealth(); // prioritise a dangerous player
            if (hp > bestHp) { bestHp = hp; best = e; }
        }
        return best;
    }

    private boolean isHuntable(LivingEntity e) {
        if (e == this || !e.isAlive() || Factions.isInfected(e)) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof Enemy;
    }

    // --- the banish beam (below 50% HP, 30s cooldown) ------------------------------------------------
    /** Fire the Limbo banish beam at {@code target} if off cooldown. Below-50%-HP gate is enforced by caller. */
    public void tryFireBeamAt(LivingEntity target) {
        if (beamCooldown > 0) return;
        net.minecraft.world.phys.Vec3 origin = getEyePosition();
        net.minecraft.world.phys.Vec3 dir = target.getEyePosition().subtract(origin).normalize();
        LimboBeam.fireFrom(this, origin, dir, net.minecraft.sounds.SoundSource.HOSTILE);
        beamCooldown = BEAM_COOLDOWN;
    }

    // --- refund + despawn at home --------------------------------------------------------------------
    /** Refund the 64 gems Darius cost to the home brain's economy, release the squad, and discard. Once-only. */
    public void refundAndDespawn() {
        if (despawning) return;
        despawning = true;
        if (level() instanceof ServerLevel sl) {
            DariusSquad.release(sl, squad);
            if (homeBrain != null) {
                BrainNodeData data = BrainNodeData.get(sl.getServer());
                if (data.blocks().contains(homeBrain)) {
                    data.econ(homeBrain).addEmeralds(64);
                    BrainReflexes.logExternal(sl, homeBrain.pos(), "General Darius returns (+64)");
                }
            }
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 1.0, getZ(), 24, 0.4, 0.6, 0.4, 0.02);
        }
        discard();
    }

    // --- boss bar player tracking --------------------------------------------------------------------
    @Override
    public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel sl) DariusSquad.release(sl, squad);
        super.remove(reason);
    }

    // --- persistence ---------------------------------------------------------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homeBrain != null) {
            tag.putString(NBT_HOME_DIM, homeBrain.dimension().location().toString());
            tag.putLong(NBT_HOME_POS, homeBrain.pos().asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_HOME_DIM) && tag.contains(NBT_HOME_POS)) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                    new ResourceLocation(tag.getString(NBT_HOME_DIM)));
            homeBrain = GlobalPos.of(dim, BlockPos.of(tag.getLong(NBT_HOME_POS)));
        }
        if (getCustomName() == null) { setCustomName(Component.literal("Darius")); setCustomNameVisible(true); }
    }

}
