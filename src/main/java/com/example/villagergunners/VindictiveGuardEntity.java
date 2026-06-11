package com.example.villagergunners;

import com.example.villagergunners.goal.StageThreeGuardGoal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A "vindictive guard": a creature the unleashed gravemind makes out of those it strikes down. It LOOKS and
 * SOUNDS like a zombie villager (vanilla {@code ZombieVillagerRenderer}) but FIGHTS exactly like a stage-3
 * Guard Villager ({@link StageThreeGuardGoal}: enchanted mace / crossbow / Alien Slasher), wears no armour,
 * never burns in daylight, and is spontaneously hostile to everything that isn't part of the infected
 * faction ({@link Factions}). It is an {@code Enemy} (a monster), so other defenders treat it as hostile --
 * but it and the boss never target each other (enforced in {@link UnleashedManager} + StageThreeGuardGoal).
 */
public class VindictiveGuardEntity extends ZombieVillager {
    public VindictiveGuardEntity(EntityType<? extends ZombieVillager> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        // Stage-3 guard combat ONLY -- no vanilla zombie goals (no super call).
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new StageThreeGuardGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Hostile to EVERYTHING that isn't infected faction: players + any mob (villagers, animals, monsters).
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e != this && !Factions.isInfected(e) && (e instanceof Player || e instanceof Mob)));
    }

    /** Never burns in daylight (the village's failure made it permanent). */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    /** No armour -- the stage-3 guard goal supplies the weapons; we add nothing. */
    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
        // intentionally empty: no armour, no random gear
    }

    /** Immune to fire (per spec); the 90% explosion reduction is applied in UnleashedManager. */
    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void setSecondsOnFire(int seconds) {
        // fire-immune: ignored
    }

    /** Vindictive guards do not convert back to villagers on their own. */
    @Override
    public boolean canBreakDoors() {
        return false;
    }
}
