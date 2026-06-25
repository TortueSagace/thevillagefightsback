package com.example.villagefightsback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Emerald Virus arrow: a normal arrow that also infects whatever living thing it hits with the Emerald
 * Virus (like a tipped arrow). Crafted cheaply from 8 arrows + a Potion of Emerald Virus. The {@code virus
 * amplifier} (0 = Virus I, 1 = Virus II) is set when the arrow is spawned and drives both the on-hit
 * infection strength and which arrow item is picked back up.
 */
public class EmeraldVirusArrowEntity extends AbstractArrow {
    private static final EntityDataAccessor<Integer> DATA_AMPLIFIER =
            SynchedEntityData.defineId(EmeraldVirusArrowEntity.class, EntityDataSerializers.INT);

    public EmeraldVirusArrowEntity(EntityType<? extends EmeraldVirusArrowEntity> type, Level level) {
        super(type, level);
    }

    public EmeraldVirusArrowEntity(Level level, LivingEntity shooter) {
        super(ModEntities.EMERALD_VIRUS_ARROW.get(), shooter, level,
                new ItemStack(ModItems.EMERALD_VIRUS_ARROW.get()), null);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AMPLIFIER, 0);
    }

    /** Virus strength this arrow inflicts on hit: 0 = Virus I, 1 = Virus II. */
    public void setVirusAmplifier(int amplifier) {
        this.entityData.set(DATA_AMPLIFIER, amplifier);
    }

    public int getVirusAmplifier() {
        return this.entityData.get(DATA_AMPLIFIER);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(getVirusAmplifier() >= 1
                ? ModItems.EMERALD_VIRUS_ARROW_2.get() : ModItems.EMERALD_VIRUS_ARROW.get());
    }

    /** Called by AbstractArrow after the hit's damage is applied -- where tipped arrows add their effects. */
    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.addEffect(new MobEffectInstance(ModEffects.EMERALD_VIRUS, EmeraldVirus.DURATION_TICKS,
                getVirusAmplifier()), this.getEffectSource());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VirusAmplifier", getVirusAmplifier());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("VirusAmplifier")) setVirusAmplifier(tag.getInt("VirusAmplifier"));
    }
}
