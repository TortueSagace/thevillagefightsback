package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The "Vindictive Gigachad": a boss minion. It is a gigachad golem (rocket launcher, iron-set armour, 200 HP)
 * that the Unleashed Gravemind summons from its jump attack. It belongs to the infected {@link Factions}, so it
 * never fights the boss or other infected, and it is hostile to everything else (players, villagers, monsters).
 *
 * It shares the base gigachad kit via {@link VillageEvents#armGolemFull(IronGolem)} -- so any change to the base
 * gigachad applies here too. The differences are: its faction, the "Vindictive Gigachad" name (set inside
 * armGolemFull), fire immunity, and the green-vine re-skin (client-side {@code VindictiveVinesLayer}).
 */
public class VindictiveGigachadEntity extends GigachadGolemEntity {
    public VindictiveGigachadEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // vanilla golem movement + melee + look
        // Hostile to everything that isn't infected: players + any mob (villagers, animals, monsters).
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e != this && !Factions.isInfected(e) && (e instanceof Player || e instanceof Mob)));
    }

    // finalizeSpawn is inherited from GigachadGolemEntity (it calls armGolemFull -> the shared gigachad kit).

    /** Fire-immune like the rest of the infected faction. */
    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void setSecondsOnFire(int seconds) {
        // fire-immune: ignored
    }
}
