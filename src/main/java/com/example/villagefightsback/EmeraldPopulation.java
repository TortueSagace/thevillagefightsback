package com.example.villagefightsback;

import java.util.Collections;
import java.util.List;

import com.example.villagefightsback.platform.PersistentData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.example.villagefightsback.platform.event.CommandEvent;
import com.example.villagefightsback.platform.event.EntityJoinLevelEvent;
import com.example.villagefightsback.platform.EventPriority;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * Soft population cap for the EMERALD faction ({@code emerald_mobs_max_number}) -- purely to limit lag, so it is
 * LOCAL: it counts the emerald units (mites included) within the player's active range of a spawn, NOT across the
 * whole world. Emeralds living far from any player aren't ticking and don't lag anyone, so they never count and
 * never block a fresh spawn elsewhere. When a NEW emerald would spawn automatically (conversion, brain summon,
 * death eruption) into an area already at the cap, the spawn is cancelled and that unit's max health is poured into
 * the nearest non-mite emerald instead. A conversion victim then simply dies and its infested path still appears.
 * Manual spawns (spawn egg / {@code /summon}) check {@link #atCap} up front and tell the player. Entities loaded
 * from disk are never capped.
 */
public final class EmeraldPopulation {
    private EmeraldPopulation() {}

    /** The player's active entity range: emeralds within this of a spawn count toward the local cap, the rest do not. */
    private static final double RANGE = 128.0;

    /** Persistent-data flag marking a DELIBERATE spawn (e.g. the brain's scout cats) that bypasses the lag cap. */
    public static final String UNCAPPED_TAG = "tvfb_uncapped";

    /** Every LIVE emerald unit counts toward the cap (mites included) -- the cap is about loaded/ticking-entity lag.
     *  Dead/dying emeralds (mid death-animation, about to be removed) are excluded so the cap count, the mite-cull
     *  baseline and the mites list all agree on the same alive population. */
    private static boolean isCapped(Entity e) {
        return Factions.isEmerald(e) && (!(e instanceof LivingEntity le) || le.isAlive());
    }

    /** A valid HP-absorption recipient: a live, NON-mite emerald (mites are too transient to bank HP into). */
    private static boolean isAbsorbTarget(Entity e) {
        return e.isAlive() && Factions.isEmerald(e) && !(e instanceof EmeraldEndermite);
    }

    /** True if the emerald population WITHIN {@link #RANGE} of {@code pos} is at/over the cap (0 = unlimited). */
    public static boolean atCap(ServerLevel level, Vec3 pos) {
        int max = Config.emeraldMobsMaxNumber();
        if (max <= 0) return false;
        AABB box = new AABB(pos, pos).inflate(RANGE);
        return level.getEntitiesOfClass(LivingEntity.class, box, EmeraldPopulation::isCapped).size() >= max;
    }

    /**
     * Master gate for emerald mobs: when EITHER "enable_mob_infection" OR "enable_emerald_infestation" is OFF, NO
     * emerald unit may enter the world via ANY path (virus-death spawn, brain summon, spawn egg, morph). Highest
     * priority so it cancels before the cap logic below. (Moved here when the old EmeraldInfection class was removed
     * in 1.1.4; gated on emerald_infestation too in 1.2.4 so that toggle fully disables the emerald faction.)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEmeraldJoinGate(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || Config.emeraldMobsEnabled()) return;
        if (Factions.isEmerald(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW) // after the enable_mob_infection gate, which may cancel first
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !isCapped(event.getEntity())) return;
        if (PersistentData.of(event.getEntity()).getBoolean(UNCAPPED_TAG)) return; // deliberate spawn -> never capped
        Vec3 pos = event.getEntity().position();
        if (!atCap(level, pos)) return;
        // At the cap. A NON-mite emerald (e.g. an Emeraldger) first makes room by culling expendable mites; each
        // culled mite buffs the surrounding emeralds. A mite never culls other mites to spawn itself.
        if (!(event.getEntity() instanceof EmeraldEndermite)) {
            cullToMakeRoom(level, pos, EmeraldEndermite.class);
            // No mites left to sacrifice? A unit MORE valuable than an Emeraldger may now sacrifice Emeraldgers too
            // (an Emeraldger never culls other Emeraldgers to spawn itself, just as a mite never culls mites).
            if (atCap(level, pos) && !(event.getEntity() instanceof EmeraldgerEntity)) {
                cullToMakeRoom(level, pos, EmeraldgerEntity.class);
            }
        }
        if (atCap(level, pos)) {                       // still full (nothing left to cull, or the joiner is too cheap)
            if (event.getEntity() instanceof LivingEntity wouldBe) absorbInto(level, wouldBe);
            event.setCanceled(true);
        }
    }

    /** Kill random emerald units of {@code type} within {@link #RANGE} of {@code pos} -- buffing nearby emeralds on
     *  each -- until the local emerald population falls below the cap or none of that type remain. Frees slots for a
     *  more valuable spawn (mites are sacrificed first; Emeraldgers only once the mites are gone). */
    private static <T extends LivingEntity> void cullToMakeRoom(ServerLevel level, Vec3 pos, Class<T> type) {
        int max = Config.emeraldMobsMaxNumber();
        if (max <= 0) return;
        AABB box = new AABB(pos, pos).inflate(RANGE);
        List<T> victims = level.getEntitiesOfClass(type, box, LivingEntity::isAlive);
        if (victims.isEmpty()) return;
        Collections.shuffle(victims);                                    // cull RANDOM victims, not the nearest
        int count = level.getEntitiesOfClass(LivingEntity.class, box, EmeraldPopulation::isCapped).size();
        for (T victim : victims) {
            if (count < max) break;                                      // enough room freed
            BlockPos vp = victim.blockPosition();
            victim.discard();                                           // remove cleanly: no drops, no death eruption
            EmeraldConversion.buffNearbyEmeralds(level, vp, null);       // each cull still feeds the swarm's buff
            count--;
        }
    }

    /** A plain "/summon <emerald entity>" into a capped area is blocked with a message, not silently absorbed. */
    @SubscribeEvent
    public static void onSummonCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        if (!input.startsWith("summon ")) return;
        if (!input.contains("thevillagefightsback:emerald") && !input.contains("thevillagefightsback:rich_sheep")) return;
        CommandSourceStack src = event.getParseResults().getContext().getSource();
        if (src.getLevel() != null && atCap(src.getLevel(), src.getPosition())) {
            src.sendFailure(Component.literal("Too many emerald units nearby (cap " + Config.emeraldMobsMaxNumber()
                    + "). No emerald unit was summoned."));
            event.setCanceled(true);
        }
    }

    /** How many times its ORIGINAL max health one emerald unit may bank before it stops accepting more. */
    private static final double MAX_HP_MULTIPLE = 5.0;

    /** Where a unit's spawn-time max health is remembered, so the ceiling is measured from a fixed point. */
    private static final String NBT_ORIGINAL_HP = "vgun_original_max_hp";

    /**
     * The max health this unit started with.
     *
     * <p>Recorded the first time anything pours HP into it, and never again. It has to be stored rather than
     * read live, because the live value is exactly what absorption inflates: measuring the ceiling against
     * "current times five" would mean every absorption raised the ceiling too, and the cap would never bind.
     */
    private static double originalMaxHp(LivingEntity e, AttributeInstance attr) {
        net.minecraft.nbt.CompoundTag tag = PersistentData.of(e);
        if (!tag.contains(NBT_ORIGINAL_HP)) tag.putDouble(NBT_ORIGINAL_HP, attr.getBaseValue());
        return tag.getDouble(NBT_ORIGINAL_HP);
    }

    /**
     * Pour {@code wouldBe}'s max health into the emerald units around it, nearest first.
     *
     * <p>At the population cap a unit that cannot spawn used to dump its whole max health into whichever
     * emerald happened to be closest, without limit, so a long siege ended with one absolutely enormous mob
     * standing where a hundred should have been. Each unit now stops at {@value #MAX_HP_MULTIPLE} times the
     * health it started with.
     *
     * <p>The overflow is not lost. It walks OUTWARD: fill the nearest unit to its ceiling, carry what is left
     * to the next nearest with room, and so on. Only when every unit in range is full does the remainder become
     * organic mass for the nearest brain. That last step cannot double-pay, because the death handler in
     * {@link EmeraldVirusEvents} deliberately skips its own banking whenever the population is at cap, on the
     * grounds that this method was already taking the payment.
     */
    private static void absorbInto(ServerLevel level, LivingEntity wouldBe) {
        double remaining = wouldBe.getMaxHealth();
        AABB box = new AABB(wouldBe.blockPosition()).inflate(RANGE);
        java.util.List<LivingEntity> nearestFirst = new java.util.ArrayList<>(
                level.getEntitiesOfClass(LivingEntity.class, box, EmeraldPopulation::isAbsorbTarget));
        nearestFirst.sort(java.util.Comparator.comparingDouble(c -> c.distanceToSqr(wouldBe)));

        for (LivingEntity c : nearestFirst) {
            if (remaining <= 0.0) break;
            AttributeInstance attr = c.getAttribute(Attributes.MAX_HEALTH);
            if (attr == null) continue;
            double headroom = originalMaxHp(c, attr) * MAX_HP_MULTIPLE - attr.getBaseValue();
            if (headroom <= 0.0) continue;                       // this one is full: try the next nearest
            double take = Math.min(headroom, remaining);
            attr.setBaseValue(attr.getBaseValue() + take);
            c.setHealth((float) Math.min(c.getMaxHealth(), c.getHealth() + take));
            remaining -= take;
        }

        if (remaining > 0.0) {
            OrganicMass.addToNearestBrain(level, wouldBe.blockPosition(), (int) Math.round(remaining));
        }
    }
}
