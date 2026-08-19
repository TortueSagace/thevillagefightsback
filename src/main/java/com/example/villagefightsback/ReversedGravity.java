package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.event.PlayerEvent;
import com.example.villagefightsback.platform.event.ServerStoppingEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * "Gravity breaks": when a holder of Darius' Special Sauce would fall out of the world, the world falls
 * instead. For five seconds the whole dimension goes up -- creatures, players, items, orbs, arrows and
 * falling blocks all rise, and every player in it sees the world upside down.
 *
 * <p>Fires in EVERY dimension, the Limbo included. Cancelling the void fall does mean a Special Sauce
 * holder can no longer ride the void between worlds -- no Overworld-to-Limbo drop, no Limbo-to-End drop
 * (see {@link VoidFallEvents}). That is the trade, and it is intended: the whole point of the effect is
 * that the void does not get to move you. Anyone without the effect relays exactly as before.
 *
 * <p><b>Nothing here is persisted, by design.</b> {@code setNoGravity} is deliberately NOT used: the flag
 * is written to entity NBT, so a crash mid-effect would leave every entity in the dimension with gravity
 * permanently off. Living entities are lifted with a hidden, self-expiring {@code LEVITATION}, and
 * everything else with a raw per-tick velocity write. Worst case after a hard kill is under five seconds
 * of invisible levitation on reload.
 *
 * <p>Levitation is also the only lift that survives a dedicated server: the vanilla anti-fly check kicks a
 * player who appears to hover for 80 consecutive ticks, and it carries an explicit
 * {@code !hasEffect(LEVITATION)} exemption. A velocity-driven lift would disconnect everybody at tick 81 of
 * this 100-tick effect, and would never show up in singleplayer, where flight is always allowed.
 *
 * <p>There is no soft landing when it ends. That is on purpose -- the drop is the joke. Villagers and
 * guards are the exception, and they save themselves: see {@link WaterClutchEvents}.
 */
public final class ReversedGravity {
    private ReversedGravity() {}

    /** Five seconds. */
    public static final int DURATION = 100;
    /**
     * Levitation tier, chosen so UP is exactly as fast as DOWN.
     *
     * <p>Falling settles at 3.92 blocks/tick: {@code v = (v - 0.08) * 0.98} has its fixed point there.
     * Levitation settles at {@code 0.907 * 0.05 * (amp + 1)}, so matching 3.92 needs amp + 1 = 86.4. Tier 85
     * gives 3.90, which is the same speed to within a rounding error.
     *
     * <p>It was tier 2 to begin with, which rises at 0.136 -- about a thirtieth of the speed of the fall it is
     * supposed to be reversing, and it showed.
     */
    private static final int RISE_AMP = 85;
    /**
     * Terminal rise for a non-living entity, as a multiple of its own downward pull.
     *
     * <p>Same arithmetic: {@code v = (v - g) * 0.98} settles at {@code 49 * g}, so capping the climb there
     * makes an item or a falling block rise at precisely the speed it would have fallen. The old flat 0.14
     * cap was the same mistake as the levitation tier, in a different place.
     */
    private static final double RISE_TERMINAL = 49.0;
    /** The rescued unit's mirrored velocity is clamped into this band, so a slow slip up is still a launch. */
    private static final double MIN_KICK = 1.2;
    /** Terminal falling speed, so mirroring a full-speed fall really does mirror it. */
    private static final double MAX_KICK = 3.92;

    /** Dimension -> game time at which the reversal there ends. Empty in the overwhelmingly common case. */
    private static final Map<ResourceKey<Level>, Long> ACTIVE = new HashMap<>();
    /** Units pulled back out of the void, checked once at the end so none is left falling again. */
    private record Rescued(ResourceKey<Level> dim, UUID id) {}
    private static final List<Rescued> RESCUED = new ArrayList<>();

    /** Reusable scratch, so the sweep never allocates and never mutates the entity list while iterating. */
    private static final List<LivingEntity> SCRATCH = new ArrayList<>();

    // --- trigger -------------------------------------------------------------------------------------

    /**
     * A holder is falling out of {@code sl}. Flip its fall into a launch, and turn the dimension over.
     * Idempotent: vanilla re-deals void damage roughly every 10 ticks, so this is called repeatedly.
     */
    public static void trigger(ServerLevel sl, LivingEntity e) {
        Vec3 m = e.getDeltaMovement();
        double up = Math.min(MAX_KICK, Math.max(MIN_KICK, Math.abs(m.y)));   // sign flipped, magnitude kept
        e.setDeltaMovement(m.x, up, m.z);
        e.resetFallDistance();
        e.hurtMarked = true;                                                 // resync, players included

        Rescued r = new Rescued(sl.dimension(), e.getUUID());
        if (!RESCUED.contains(r)) RESCUED.add(r);

        // The flip above happens on every re-hit; everything below is once per reversal. Vanilla re-deals
        // void damage about every 10 ticks, so without this the swirl and the packet would stutter out
        // five times over.
        long want = sl.getGameTime() + DURATION;
        Long cur = ACTIVE.get(sl.dimension());
        if (cur != null && want <= cur + 20L) return;                        // the 10-tick re-hit, not a new one
        ACTIVE.put(sl.dimension(), want);
        LimboBanishFx.start(e);                                              // the void spits it back out
        broadcast(sl, DURATION);
    }

    // --- the field -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<ResourceKey<Level>, Long>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceKey<Level>, Long> en = it.next();
            ServerLevel sl = event.getServer().getLevel(en.getKey());
            if (sl == null) { it.remove(); continue; }
            long remaining = en.getValue() - sl.getGameTime();
            if (remaining <= 0) { it.remove(); land(sl); continue; }
            sweep(sl, (int) remaining);
        }
    }

    /**
     * Turn everything in {@code sl} upward for one tick.
     *
     * <p>{@code getAllEntities()} is NOT the world-wide entity query this codebase bans. That rule is about
     * {@code getEntitiesOfClass} with a world-sized AABB, whose cost is driven by the BOX VOLUME -- it walks
     * every 16x16x16 section coordinate inside it and is enormous even in an empty world. This is a flat
     * iteration of one map, sized by loaded entity count: tens of microseconds for a normal dimension, and
     * only for the 100 ticks the reversal lasts.
     */
    private static void sweep(ServerLevel sl, int remaining) {
        double floor = sl.getMinBuildHeight() - 32.0;
        for (Entity e : sl.getAllEntities()) {
            if (e.isRemoved() || e.isPassenger()) continue;
            if (e instanceof LivingEntity le) {
                // Applied ONCE per creature, not per tick: re-adding would emit an effect packet per
                // creature per tick, which is a real network sink at village scale.
                if (!le.hasEffect(MobEffects.LEVITATION)) SCRATCH.add(le);
                continue;
            }
            if (e.isNoGravity()) continue;             // fireballs, shulker bullets, anything already flying
            Vec3 m = e.getDeltaMovement();
            double y = m.y;
            if (e.getY() < floor) {
                y = Math.abs(y) + 0.2;                 // about to be deleted by the void: throw it back up
            } else {
                double g = gravityOf(e);
                double cap = RISE_TERMINAL * g;                  // its own falling speed, upward
                if (y < cap) y = Math.min(cap, y + 2.0 * g);     // cancel this tick's gravity, then add it back up
            }
            if (y != m.y) e.setDeltaMovement(m.x, y, m.z);
        }
        // Applied after the walk: addEffect fires MobEffectEvent.Added, and a listener that spawned or
        // removed an entity from inside the loop would wreck the iterator.
        if (!SCRATCH.isEmpty()) {
            for (LivingEntity le : SCRATCH) {
                le.addEffect(new MobEffectInstance(MobEffects.LEVITATION, remaining, RISE_AMP,
                        false, false, false));
            }
            SCRATCH.clear();
        }
    }

    /**
     * The reversal is over. Nothing is cushioned -- everything simply falls, which is the point. The one
     * thing checked is that a unit we pulled out of the void is not still IN the void, because that would
     * re-trigger the whole thing and loop forever.
     */
    private static void land(ServerLevel sl) {
        Iterator<Rescued> it = RESCUED.iterator();
        while (it.hasNext()) {
            Rescued r = it.next();
            if (!r.dim.equals(sl.dimension())) continue;
            it.remove();
            Entity e = sl.getEntity(r.id);
            if (e == null || e.isRemoved() || e.getY() >= sl.getMinBuildHeight()) continue;
            // Its own column is loaded (it is standing in it), so this is an array read, not a chunk load.
            int h = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, e.getBlockX(), e.getBlockZ());
            double y = h > sl.getMinBuildHeight() ? h + 2.0 : sl.getMaxBuildHeight() - 8.0;
            e.teleportTo(e.getX(), y, e.getZ());
            e.resetFallDistance();
            e.hurtMarked = true;
        }
    }

    /** Vanilla per-tick downward pull, by entity kind. A miss here just makes something drift slightly off. */
    private static double gravityOf(Entity e) {
        if (e instanceof ExperienceOrb) return 0.03;
        if (e instanceof AbstractArrow) return 0.05;
        if (e instanceof ThrowableProjectile) return 0.03;
        if (e instanceof ItemEntity) return 0.04;
        return 0.04;
    }

    // --- client sync ---------------------------------------------------------------------------------

    private static void broadcast(ServerLevel sl, int ticks) {
        if (!Config.REVERSED_GRAVITY_FLIPS_SCREEN.get()) return;   // physics still reversed, camera left alone
        com.example.villagefightsback.network.ModNetwork.sendToDimension(sl.dimension(),
                new com.example.villagefightsback.network.ReversedGravityPacket(ticks));
    }

    /** Somebody walking into a flipped dimension gets the rest of the flip. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        resync(sp, event.getTo());
    }

    /** Same for somebody logging in while it is running. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        resync(sp, sp.level().dimension());
    }

    private static void resync(ServerPlayer sp, ResourceKey<Level> dim) {
        Long end = ACTIVE.get(dim);
        if (end == null || !(sp.level() instanceof ServerLevel sl)) return;
        int remaining = (int) (end - sl.getGameTime());
        if (remaining > 0 && Config.REVERSED_GRAVITY_FLIPS_SCREEN.get()) {
            com.example.villagefightsback.network.ModNetwork.sendTo(sp,
                    new com.example.villagefightsback.network.ReversedGravityPacket(remaining));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
        RESCUED.clear();
        SCRATCH.clear();
    }
}
