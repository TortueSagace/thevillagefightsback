package com.example.villagefightsback;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Shared behaviour for the EMERALD faction (v1.0.3 "mob infection"). Each {@code EmeraldX} entity calls
 * {@link #addEmeraldGoals} from its {@code registerGoals()} so they all hunt the same way.
 *
 * <p>An emerald unit is hostile to anything that is NOT (rule 4): another emerald unit, a player without the
 * Enemy-of-the-Village status, a village-faction unit, or a vindictive-faction unit -- i.e. it attacks vanilla
 * hostiles, peaceful animals, and Enemy-of-the-Village players, snowballing the faction across the world.
 * Conversion, the on-hit Emerald Infection, loot and the death effects are wired separately in
 * {@link EmeraldInfection}.
 */
public final class EmeraldMobs {
    private EmeraldMobs() {}

    /** A target an emerald unit will hunt: alive, and none of the four spared conditions. */
    public static boolean isHostileTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (Factions.isEmerald(target)) return false;        // (i) fellow emerald unit
        if (Factions.isVillage(target)) return false;        // (iii) villager/guard/golem/wall defender/Darius
        if (Factions.isInfected(target)) return false;       // (iv) vindictive faction
        if (target instanceof Player p) {                    // (ii) a player only if Enemy of the Village
            return !p.isCreative() && !p.isSpectator()
                    && p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get());
        }
        return true;                                         // everything else (hostiles + peaceful animals)
    }

    /** Wire an emerald unit's goals: drop the vanilla targeting (a zombie's hunt-villagers/players is wrong for
     *  this faction), then counter-attack when provoked + hunt every non-spared target. A PASSIVE species also
     *  gets a melee attack (its damage = 50% of max health, set on the entity's attributes). Call AFTER
     *  super.registerGoals() so the species keeps its movement/attack goals but not its target selection. */
    public static void addEmeraldGoals(Mob mob, boolean passive) {
        mob.targetSelector.removeAllGoals(g -> true); // replace vanilla targeting with the emerald-faction rules
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                mob, LivingEntity.class, 10, true, false, t -> isHostileTarget(t)));
        // Goals that need a PathfinderMob (a Bat is a plain Mob, so it just gets the target scan above).
        if (mob instanceof PathfinderMob pm) {
            pm.targetSelector.addGoal(1, new HurtByTargetGoal(pm));
            if (passive) pm.goalSelector.addGoal(2, new MeleeAttackGoal(pm, 1.25D, true));
        }
    }

    /** The melee damage a passive emerald species deals: 50% of its max health (rule 4). */
    public static double passiveAttackDamage(double maxHealth) {
        return Math.max(1.0, maxHealth * 0.5);
    }
}
