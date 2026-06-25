package com.example.villagefightsback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;

/**
 * The Emeraldger: a stage-4 evolution of the Emerald Virus. When a virus host dies (and explodes), one of
 * these rises from the corpse. It is mechanically a stage-3 villager (Alien Slasher + leather-grade armour +
 * the same combat AI, applied by {@link VillageEvents}) with three differences:
 *  - It can never take a profession or a mod role (miner/settler/builder) -- see the role guards keyed on
 *    this type.
 *  - Its HP is 50% of the creature that died, and its body is scaled to that HP (villager-sized at 20 HP,
 *    double at 40 HP, ...), clamped to a half-block / 5 HP minimum and a 10-block / 200 HP maximum.
 *  - It despawns 5 minutes after spawning unless it kills something (each kill renews the 5-minute window).
 * Its eyes glow shiny green (client {@code EmeraldgerRenderer}). Being a Villager, it is virus-immune and an
 * ally just like a normal villager.
 */
public class EmeraldgerEntity extends Villager {
    /** Approx. vanilla villager standing height; the scale of 1.0 (== 20 HP) matches a normal villager. */
    public static final float VILLAGER_HEIGHT = 1.95F;
    public static final float MIN_HP = 5.0F;
    public static final float MAX_HP = 200.0F;
    private static final float MIN_HEIGHT = 0.5F;
    private static final float MAX_HEIGHT = 10.0F;
    /** No-kill lifespan: despawn 5 minutes after spawn / last kill. */
    public static final long NO_KILL_TICKS = 5L * 60L * 20L; // 6000

    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(EmeraldgerEntity.class, EntityDataSerializers.FLOAT);

    private long despawnAt = Long.MAX_VALUE; // game time at which it despawns if it hasn't killed anything

    public EmeraldgerEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        setPersistenceRequired(); // never naturally despawns; only the no-kill timer removes it
    }

    /** Body scale for an HP value: scale = HP/20 (villager-sized at 20 HP), clamped to [0.5, 10] blocks tall. */
    public static float scaleForHp(float hp) {
        float s = hp / 20.0F;
        return Mth.clamp(s, MIN_HEIGHT / VILLAGER_HEIGHT, MAX_HEIGHT / VILLAGER_HEIGHT);
    }

    /** Configure a fresh emeraldger from the dead host's MAX health: HP = 50% of it (clamped), scale, timer. */
    public void initWithMaxHealthOfDead(float deadMaxHealth) {
        float hp = Mth.clamp(deadMaxHealth * 0.5F, MIN_HP, MAX_HP);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth(hp);
        // Body size tracks HP only when emeraldgers_size_variation is on; otherwise a uniform default villager size.
        setEmeraldgerScale(Config.emeraldgerSizeVariation() ? scaleForHp(hp) : 1.0F);
        this.despawnAt = level().getGameTime() + NO_KILL_TICKS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SCALE, 1.0F);
    }

    public float getEmeraldgerScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setEmeraldgerScale(float scale) {
        this.entityData.set(DATA_SCALE, scale);
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_SCALE.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale(getEmeraldgerScale());
    }

    /** Never let a profession stick: emeraldgers stay professionless (and so never trade or take a job). */
    @Override
    public void setVillagerData(VillagerData data) {
        super.setVillagerData(data.setProfession(VillagerProfession.NONE));
    }

    /** Renew the 5-minute window whenever this emeraldger kills something. */
    public void markKill() {
        this.despawnAt = level().getGameTime() + NO_KILL_TICKS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && level().getGameTime() >= despawnAt) {
            discard(); // 5 minutes with no kills -> gone
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // only the no-kill timer despawns it
    }

    // --- total, permanent immunity (silent: no effect particles, since no effect ever lands) -------

    /** Immune to fire/lava: never takes fire damage and never visually burns. */
    @Override
    public boolean fireImmune() {
        return true;
    }

    /** Never catch fire (e.g. from the Alien Slasher's on-hit fire) -- belt-and-braces with fireImmune(). */
    @Override
    public void igniteForTicks(int ticks) {
        // total immunity: ignored.
    }

    /** Immune to every status effect EXCEPT the emerald death-buff (Strength/Resistance/Regeneration), which it
     *  receives like any other emerald unit when a nearby emerald dies. Everything else (virus, wither, ...) is
     *  rejected here, so no particles and no harm. */
    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e = effect.getEffect();
        return e == net.minecraft.world.effect.MobEffects.DAMAGE_BOOST
                || e == net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE
                || e == net.minecraft.world.effect.MobEffects.REGENERATION;
    }

    /** Immune to freezing in powder snow too (it isn't a MobEffect, so it needs its own guard). */
    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("EmeraldgerScale", getEmeraldgerScale());
        tag.putLong("EmeraldgerDespawnAt", despawnAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("EmeraldgerScale")) setEmeraldgerScale(tag.getFloat("EmeraldgerScale"));
        if (tag.contains("EmeraldgerDespawnAt")) despawnAt = tag.getLong("EmeraldgerDespawnAt");
    }
}
