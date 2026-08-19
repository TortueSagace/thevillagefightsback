package com.example.villagefightsback;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.example.villagefightsback.platform.ModRegistries;

/**
 * The "infected" faction: the unleashed Village Brain Node boss and the vindictive guards it makes. Members
 * never target or harm one another; everything else is fair game. Used by the boss targeting, the vindictive
 * guard combat goal, and the friendly-fire / change-target event hooks in {@link UnleashedManager}.
 */
public final class Factions {
    private Factions() {}

    /** True for the unleashed brainNode boss and its minions (vindictive guards + vindictive gigachads). */
    public static boolean isInfected(Entity e) {
        return e instanceof UnleashedBrainNodeEntity
                || e instanceof VindictiveGuardEntity
                || e instanceof VindictiveGigachadEntity;
    }

    /**
     * General Darius is the village's SUMMONED CHAMPION (a stage-4 brain reflex). He extends {@code Monster}, so
     * he is technically an {@code Enemy}, but the village must treat him as one of its own and never target,
     * flee, shoot, or harm him. Every village-side threat scan excludes him with this check. (The boss faction --
     * the unleashed brain node and its vindictive minions -- is NOT filtered here: it rightly fights Darius.)
     */
    public static boolean isVillageChampion(Entity e) {
        return e instanceof GeneralDariusEntity;
    }

    /**
     * Entities the VILLAGE (villagers, guards, golems, wall defenders, the sage) must never target or harm,
     * even though they read as a threat:
     * <ul>
     *   <li>General Darius -- the village's own summoned champion;</li>
     *   <li>the Vindictive Village Spirit ({@link UnleashedBrainNodeEntity}) -- it is friendly toward villagers.
     *       Its TWO minions (vindictive guards + gigachads) are NOT spared: the village still fights those.</li>
     *   <li>every EMERALD unit ({@link #isEmerald}) -- the emerald faction and the village leave each other alone
     *       (v1.0.3). This covers the village's custom turret/beam targeting too, not just the AI goals.</li>
     * </ul>
     */
    public static boolean isSparedByVillage(Entity e) {
        return e instanceof GeneralDariusEntity || e instanceof UnleashedBrainNodeEntity || isEmerald(e);
    }

    /** The Crackers' Wither Storm boss entity id ({@code witherstormmod:wither_storm}); an optional soft dep. */
    public static final ResourceLocation WITHER_STORM_ID = new ResourceLocation("witherstormmod", "wither_storm");

    /** True for a wither storm. Matched by entity id, so the Wither Storm mod stays an optional soft dependency. */
    public static boolean isWitherStorm(Entity e) {
        return WITHER_STORM_ID.equals(ModRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    /** The wither storm's BODY entities (the core + its heads, segments and tentacles) -- everything that makes
     *  up the storm, so a banish can remove the whole creature, not just leave dangling parts behind. */
    private static final java.util.Set<String> WITHER_STORM_PARTS = java.util.Set.of(
            "wither_storm", "wither_storm_head", "wither_storm_segment", "tentacle", "tentacle_spike");

    /** True for any part of a wither storm's body (core / head / segment / tentacle). Used to banish the lot. */
    public static boolean isWitherStormPart(Entity e) {
        ResourceLocation id = ModRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && "witherstormmod".equals(id.getNamespace()) && WITHER_STORM_PARTS.contains(id.getPath());
    }

    /**
     * The nearest living wither storm within {@code radius} of {@code center}, or null if none. Scans ALL loaded
     * entities by type and POSITION -- never an AABB query -- so the storm is still found when it has DEACTIVATED
     * its hitbox (a box sweep would miss a hitbox-less entity). The wither storm gets special treatment as the
     * village's apex threat: air strikes, Darius, and the Limbo banish beam all prioritise it.
     */
    public static LivingEntity findNearestWitherStorm(ServerLevel level, Vec3 center, double radius) {
        double best = radius * radius;
        LivingEntity nearest = null;
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || !isWitherStorm(le)) continue;
            double d = le.distanceToSqr(center);
            if (d <= best) { best = d; nearest = le; }
        }
        return nearest;
    }

    /** The "limbo" faction: creatures native to Limbo -- the Limbo Phantom and the Limbo raiders (pillager /
     *  vindicator / evoker / ravager / vex). They never attack one another, so phantoms and raiders fight the
     *  village side by side instead of each other. */
    public static boolean isLimbo(Entity e) {
        return e instanceof LimboPhantomEntity || e instanceof LimboRaider;
    }

    /** The Guard Villagers guard entity id (soft dep). */
    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    /**
     * The single definition of "this is one of the village's GUARDS", in either of the two forms a guard can
     * take: the Guard Villagers guard entity, or an MCA Reborn villager wearing a soldier profession
     * ({@code mca:guard} / {@code mca:archer}).
     *
     * <p>The MCA form is why this exists. An MCA guard is a {@link net.minecraft.world.entity.npc.Villager}, so
     * any code testing the guard ENTITY ID alone silently demotes it to a civilian: it was counted as a villager
     * in the brain node's report ("Guards: 0" with a full garrison), skipped by the stage-2 guard upgrade, and
     * passed over by the mega-brutal buff and by Darius when he conscripts a squad.
     */
    public static boolean isGuard(Entity e) {
        return GUARD_ID.equals(ModRegistries.ENTITY_TYPES.getKey(e.getType()))
                || com.example.villagefightsback.compat.MCACompat.isMcaGuard(e);
    }

    /** True for the village's own (non-infected) defenders: villagers (incl. emeraldgers), iron golems, wall
     *  defenders, General Darius, and Guard Villagers guards. Vindictive units (infected) are NOT village. */
    public static boolean isVillage(Entity e) {
        if (isInfected(e)) return false; // a vindictive gigachad is an IronGolem -> exclude the boss faction
        if (isEmerald(e)) return false;  // an emerald iron golem is an IronGolem -> it's emerald, not village
        // An MCA Reborn outlaw / cultist is a Villager carrying a hostile profession -- a raider, not a citizen.
        if (com.example.villagefightsback.compat.MCACompat.isHostileVillager(e)) return false;
        return e instanceof net.minecraft.world.entity.npc.Villager
                || e instanceof net.minecraft.world.entity.animal.IronGolem
                || e instanceof WallDefenderEntity
                || e instanceof GeneralDariusEntity
                || GUARD_ID.equals(ModRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    /**
     * Everything the VILLAGE should fight: anything implementing Minecraft's {@link net.minecraft.world.entity.monster.Enemy}
     * (which covers all vanilla and modded hostiles), the vindictive boss faction, and MCA Reborn's hostile
     * villager professions (outlaw / cultist), which are {@code Enemy}-less {@code Villager}s and so would
     * otherwise be invisible to every threat scan.
     *
     * <p>The single definition of "a threat to the village", shared by the villager/guard/golem target scans.
     */
    public static boolean isVillageThreat(Entity e) {
        return e instanceof net.minecraft.world.entity.monster.Enemy
                || isInfected(e)
                || com.example.villagefightsback.compat.MCACompat.isHostileVillager(e);
    }

    /** The "emerald" faction (the v1.0.3 mob-infection units + the Emeraldger). Membership = the {@link EmeraldUnit}
     *  marker, plus the {@link EmeraldgerEntity} (a Villager subclass, so it can't carry the marker, but it is a
     *  full emerald unit: it counts toward the cap, joins the emerald death-buff, and is village-allied like the rest). */
    public static boolean isEmerald(Entity e) {
        return e instanceof EmeraldUnit || e instanceof EmeraldgerEntity;
    }
}
