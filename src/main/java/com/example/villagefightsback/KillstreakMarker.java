package com.example.villagefightsback;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * KILLSTREAK auto-marker (v1.2.1). When a single enemy mob or player kills MORE THAN {@link #KILL_THRESHOLD}
 * village/emerald units within {@link #WINDOW} ticks (5 minutes), the brain automatically paints it with the
 * {@link EmeraldMarker} so idle emerald units rally on it. (Rallying never pulls a unit out of a fight or off a
 * chase -- {@code RallyToMarkedGoal} only redirects units that have no current target.)
 *
 * <p>At most ONE creature is killstreak-marked at a time: when several killers qualify at once the STRONGEST
 * (highest max health) takes the slot, and a stronger newcomer evicts a weaker holder. This is independent of
 * the {@code OrganicMass} scent (which marks distant prey); both feed the same marker registry.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KillstreakMarker {
    private KillstreakMarker() {}

    private static final int KILL_THRESHOLD = 3;       // MORE THAN this many kills qualifies (so the 4th kill triggers)
    private static final long WINDOW = 20L * 60L * 5L; // kills are counted over a 5-minute sliding window

    // killer -> recent kill game-times, kept pruned to the window. WeakHashMap so dead/unloaded killers drop out.
    private static final Map<LivingEntity, Deque<Long>> KILLS = new WeakHashMap<>();
    // The single creature currently killstreak-marked (enforces "only one at a time"); null = none.
    private static LivingEntity current = null;

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!Config.mobInfectionEnabled()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        // Only the deaths of the village's defenders + emerald units count toward a killstreak.
        if (!Factions.isVillage(victim) && !Factions.isEmerald(victim)) return;
        // getEntity() is the responsible killer (the shooter for a projectile, not the arrow).
        if (!(event.getSource().getEntity() instanceof LivingEntity killer) || !killer.isAlive()) return;
        if (Factions.isVillage(killer) || Factions.isEmerald(killer)) return; // friendly fire never paints a target

        long now = level.getGameTime();
        Deque<Long> times = KILLS.computeIfAbsent(killer, k -> new ArrayDeque<>());
        times.addLast(now);
        prune(times, now);

        reevaluate(level, now);
    }

    /** Re-pick the killstreak slot: the strongest killer currently OVER the threshold gets the single mark. */
    private static void reevaluate(ServerLevel level, long now) {
        LivingEntity best = null;
        double bestHp = -1.0;
        Iterator<Map.Entry<LivingEntity, Deque<Long>>> it = KILLS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Deque<Long>> e = it.next();
            LivingEntity killer = e.getKey();
            Deque<Long> times = e.getValue();
            prune(times, now);
            if (killer == null || !killer.isAlive() || times.isEmpty()) { it.remove(); continue; }
            if (times.size() <= KILL_THRESHOLD) continue;          // not over the threshold (need MORE than 3)
            double hp = killer.getMaxHealth();
            if (hp > bestHp) { bestHp = hp; best = killer; }       // strongest wins ties broken by first-seen
        }

        if (best == null) {                                        // nobody qualifies -> drop a dead/stale holder
            if (current != null && (!current.isAlive() || !EmeraldMarker.isMarked(current))) current = null;
            return;
        }
        if (best == current) {                                     // same holder -> refresh its mark
            EmeraldMarker.mark(level, best);
            return;
        }
        if (current != null) EmeraldMarker.unmark(current);        // switch the single slot to the new strongest
        current = best;
        EmeraldMarker.mark(level, best);
    }

    private static void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() > WINDOW) times.removeFirst();
    }
}
