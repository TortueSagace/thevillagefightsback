package com.example.villagefightsback.client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-only helper that decides when a villager / guard should wear the "angry face" overlay.
 *
 * A mob's attack target is NOT synced to clients, so we cannot read {@code getTarget()} here. The arm
 * SWING animation IS synced, though: a mob that is actively meleeing swings its arm roughly every 0.6s.
 * We use that as the fight signal -- every time we see the entity mid-swing we refresh a per-entity
 * "angry until" deadline to ~60 ticks (~3s) in the future. While that deadline is still in the future
 * the face stays angry, so it reads as a continuous scowl all through a fight and only fades ~3s after
 * the last swing.
 *
 * Timing uses the client level's game time ({@link net.minecraft.world.level.Level#getGameTime()}) so it
 * advances in lockstep with the render/tick loop (and pauses correctly with the game).
 */
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AngryFaceClient {
    private AngryFaceClient() {}

    /** How long after the last seen swing the face stays angry (ticks). ~3s at 20 tps. */
    private static final long ANGRY_TICKS = 60L;

    /** entity id -> client game time (tick) at/after which the face reverts to normal. */
    private static final Map<Integer, Long> ANGRY_UNTIL = new ConcurrentHashMap<>();

    /** Last game time we ran the opportunistic stale-entry sweep, to avoid sweeping every call. */
    private static long lastCleanup = Long.MIN_VALUE;

    /**
     * @return true if {@code entity} should currently render the angry face. Also REFRESHES the timer
     *         when the entity is mid-swing, so callers can just invoke this once per frame in a layer.
     */
    public static boolean isAngry(LivingEntity entity) {
        if (entity == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false; // no client level -> nothing to time against
        long now = mc.level.getGameTime();

        // A FIGHT signal counts -- never a work swing. A villager mining swings an iron pickaxe and a builder
        // swings a block; the face must stay normal for those. So we refresh the angry timer only when the mob is
        // either mid-swing (melee) OR drawing a ranged weapon (a guard spends most of a fight at range with a
        // crossbow, where there is no melee swing), AND holding an actual combat weapon (see isCombatItem).
        if ((isSwinging(entity) || isDrawingRanged(entity)) && isCombatItem(entity)) {
            ANGRY_UNTIL.put(entity.getId(), now + ANGRY_TICKS);
        }

        maybeCleanup(now);

        Long until = ANGRY_UNTIL.get(entity.getId());
        return until != null && until > now;
    }

    /**
     * True while the arm-swing animation is in progress. {@link LivingEntity#swinging} is set for the
     * whole duration of a swing and is synced to the client; {@code swingTime > 0} is a cheap fallback
     * that stays true through the swing's count-up (the per-mob swing duration is private, so we don't
     * compare against it -- any positive swingTime means a swing is currently animating).
     */
    private static boolean isSwinging(LivingEntity entity) {
        return entity.swinging || entity.swingTime > 0;
    }

    /**
     * True while the entity is actively drawing/charging a ranged weapon -- the synced "use item" animation. This
     * is the RANGED fight tell: a crossbow guard never melee-swings, but it charges its crossbow each shot, so
     * {@code isUsingItem()} pulses true throughout a ranged fight (and the 3s window bridges the gaps between shots).
     */
    private static boolean isDrawingRanged(LivingEntity entity) {
        return entity.isUsingItem();
    }

    /**
     * True only when the swung item is an actual COMBAT weapon -- so a fight scowl never appears for a work
     * swing (mining, building, farming). Miner villagers swing an iron pickaxe; builders swing blocks; both
     * are excluded. The village arms its defenders with swords/axes/maces/crossbows/bows: AlienSlasher and
     * AlienEradicator extend AxeItem, and AlienSmasher extends MaceItem, so the base-class checks cover them.
     */
    private static boolean isCombatItem(LivingEntity entity) {
        net.minecraft.world.item.Item item = entity.getMainHandItem().getItem();
        return item instanceof net.minecraft.world.item.SwordItem
                || item instanceof net.minecraft.world.item.AxeItem
                || item instanceof net.minecraft.world.item.TridentItem
                || item instanceof net.minecraft.world.item.CrossbowItem
                || item instanceof net.minecraft.world.item.BowItem
                || item instanceof com.example.villagefightsback.MaceItem;
    }

    /** Drop entries that have expired well in the past so the map does not grow without bound. */
    private static void maybeCleanup(long now) {
        if (now - lastCleanup < ANGRY_TICKS) return; // sweep at most once per ANGRY_TICKS window
        lastCleanup = now;
        Iterator<Map.Entry<Integer, Long>> it = ANGRY_UNTIL.entrySet().iterator();
        while (it.hasNext()) {
            Long until = it.next().getValue();
            if (until == null || until <= now) it.remove();
        }
    }
}
