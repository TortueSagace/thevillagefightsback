package com.example.villagefightsback.goal;

import java.util.EnumSet;
import java.util.function.Supplier;

import com.example.villagefightsback.EmeraldPopulation;
import com.example.villagefightsback.LimboBanishFx;

import com.example.villagefightsback.platform.PersistentData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Environment morph for emerald ANIMALS (passive emerald units): one that stays in the wrong element long
 * enough transforms into the faction's matching form, playing the green emerald swirl. Two uses, wired in
 * {@link com.example.villagefightsback.EmeraldMobs}:
 * <ul>
 *   <li>a land animal that stays IN WATER for 10s -> Emerald Salmon;</li>
 *   <li>an Emerald Salmon stranded on DRY LAND for 5s -> Emerald Chicken.</li>
 * </ul>
 * Leaving the triggering element resets the timer. The morphed unit is tagged uncapped so this net-neutral
 * swap is never cancelled by the population cap, and inherits a custom name + full health.
 */
public class EmeraldMorphGoal extends Goal {
    private static final int GRACE = 40; // tolerate brief (<=2s) exits from the element before the timer resets

    private final Mob mob;
    private final Supplier<? extends EntityType<? extends Mob>> target;
    private final boolean whenInWater; // true: count time IN water; false: count time on DRY land
    private final int threshold;       // ticks of (near-)continuous exposure before the morph fires
    private int ticks;                 // exposure accumulated in the element
    private int outTicks;              // consecutive ticks spent OUT of the element (resets `ticks` past GRACE)

    public EmeraldMorphGoal(Mob mob, Supplier<? extends EntityType<? extends Mob>> target,
            boolean whenInWater, int threshold) {
        this.mob = mob;
        this.target = target;
        this.whenInWater = whenInWater;
        this.threshold = threshold;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class)); // a passive timer: never blocks movement/combat goals
    }

    // A persistent ticker: it always runs (no AI flags) and counts exposure itself, so a floating mob that bobs in
    // and out of the water surface each tick (the vanilla FloatGoal makes it jump/swim up) doesn't keep resetting
    // the timer the way a canUse/stop()-gated goal would.
    @Override public boolean canUse() { return mob.isAlive(); }
    @Override public boolean canContinueToUse() { return mob.isAlive(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { ticks = 0; outTicks = 0; }

    /** True while the mob is in the element that drives this morph (in water, or on dry land). */
    private boolean triggering() {
        return whenInWater == mob.isInWater();
    }

    @Override
    public void tick() {
        if (triggering()) {
            ticks++;
            outTicks = 0;
        } else if (++outTicks > GRACE) {
            ticks = 0; // truly left the element -> reset the timer
        }
        if (ticks < threshold) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!com.example.villagefightsback.Config.emeraldMobsEnabled()) { ticks = 0; return; } // emerald faction disabled
        Mob morphed = target.get().create(level);
        if (morphed == null) { ticks = 0; return; }
        morphed.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        morphed.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.CONVERSION, null, null);
        PersistentData.of(morphed).putBoolean(EmeraldPopulation.UNCAPPED_TAG, true); // net-neutral transform
        if (mob.hasCustomName()) {
            morphed.setCustomName(mob.getCustomName());
            morphed.setCustomNameVisible(mob.isCustomNameVisible());
        }
        morphed.setHealth(morphed.getMaxHealth());
        LimboBanishFx.startLime(mob);  // the green emerald swirl at the spot of the change
        level.addFreshEntity(morphed);
        mob.discard();
    }
}
