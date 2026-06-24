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
 * The on-hit Emerald Virus, loot and the death effects (mite/emeraldger spawn, buffs) are wired separately in
 * {@link EmeraldVirusEvents} and {@link EmeraldConversion}.
 */
public final class EmeraldMobs {
    private EmeraldMobs() {}

    /** A target an emerald unit will hunt: alive, and none of the four spared conditions. */
    public static boolean isHostileTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (target instanceof net.minecraft.world.entity.decoration.ArmorStand) return false; // inanimate prop, never prey/scent
        if (Factions.isEmerald(target)) return false;        // (i) fellow emerald unit
        if (Factions.isVillage(target)) return false;        // (iii) villager/guard/golem/wall defender/Darius
        if (Factions.isInfected(target)) return false;       // (iv) vindictive faction
        if (target instanceof Player p) {                    // (ii) a player only if the config opts in + Enemy of the Village
            return Config.emeraldMobsHostileToPlayer()
                    && !p.isCreative() && !p.isSpectator()
                    && p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get());
        }
        if (isPlayerCompanion(target)) return false;         // (v) the player's potential mounts/pets are not hunted
        return true;                                         // everything else (hostiles + peaceful animals)
    }

    /** Animals the player tames, rides or keeps as pets -- horses (and donkeys/mules/etc.), dogs (wolves) and
     *  cats. The emerald faction never PROACTIVELY hunts these (tamed or not) so it can't kill the player's
     *  mounts/pets -- but it still counter-attacks one that strikes it first (handled in the hurt handler). */
    public static boolean isPlayerCompanion(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.animal.horse.AbstractHorse
                || e instanceof net.minecraft.world.entity.animal.Wolf
                || e instanceof net.minecraft.world.entity.animal.Cat;
    }

    /** Wire an emerald unit's goals: drop the vanilla targeting (a zombie's hunt-villagers/players is wrong for
     *  this faction), then counter-attack when provoked + hunt every non-spared target. A PASSIVE species also
     *  gets a melee attack (its damage = 50% of max health, set on the entity's attributes). Call AFTER
     *  super.registerGoals() so the species keeps its movement/attack goals but not its target selection. */
    public static void addEmeraldGoals(Mob mob, boolean passive) {
        mob.targetSelector.removeAllGoals(g -> true); // replace vanilla targeting with the emerald-faction rules
        // mustSee=false: hunt prey even with no line of sight (line of sight is a collision raycast that GLASS and
        // walls block, so otherwise an enclosed pig in a glass dome is ignored). mustReach=false: target it even
        // when there is no walking path -- the dig goal then breaks through to reach it.
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                mob, LivingEntity.class, 10, false, false, t -> isHostileTarget(t)));
        // Low-priority rally: an IDLE unit runs at the brain's nearest Emerald Marker (below real combat goals,
        // so a unit already fighting/retaliating ignores it).
        mob.targetSelector.addGoal(3, new com.example.villagefightsback.goal.RallyToMarkedGoal(mob));
        // Goals that need a PathfinderMob (a Bat/Phantom is a plain Mob; they retaliate via the central
        // EmeraldConversion.onEmeraldHurt handler instead, and attack through their own flight goals).
        if (mob instanceof PathfinderMob pm) {
            pm.targetSelector.addGoal(1, new HurtByTargetGoal(pm));
            // Dig through soft/infectable terrain to reach prey it can't path to (only fires when the navigation is
            // stuck; breaks the front columns / the block above the head at bare-hand speed). See EmeraldDigGoal.
            pm.goalSelector.addGoal(5, new com.example.villagefightsback.goal.EmeraldDigGoal(pm));
            if (passive) {
                // An infected passive species is an AGGRESSOR, not prey: drop its flee-when-hurt goal and give the
                // melee a HIGH priority so it commits to the kill instead of wandering/breeding/fleeing.
                pm.goalSelector.removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.PanicGoal);
                pm.goalSelector.addGoal(1, new MeleeAttackGoal(pm, 1.25D, true));
            }
        }
        // A passive species deals 50% of its max health as melee (rule 4), set on its live attribute so it tracks
        // the actual max HP rather than a hardcoded literal. Requires the entity to register an ATTACK_DAMAGE attr.
        if (passive) {
            var atk = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(passiveAttackDamage(mob.getMaxHealth()));
        }
    }

    /** The melee damage a passive emerald species deals: 50% of its max health (rule 4). */
    public static double passiveAttackDamage(double maxHealth) {
        return Math.max(1.0, maxHealth * 0.5);
    }

    /** Make a unit a SCOUT: roam widely when it has no target so it actively goes looking for infection sources
     *  (the brain-summoned emerald cats + bats). Pair with a large FOLLOW_RANGE so it detects targets from afar. */
    public static void addExplorer(PathfinderMob mob) {
        // Scouts head OUTWARD (away from the nearest brain node) to leave the village and hunt, rather than
        // milling around their spawn. See ScoutRoamGoal.
        mob.goalSelector.addGoal(6, new com.example.villagefightsback.goal.ScoutRoamGoal(mob, 1.2D));
    }
}
