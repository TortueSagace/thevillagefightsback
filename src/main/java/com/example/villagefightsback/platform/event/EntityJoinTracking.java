package com.example.villagefightsback.platform.event;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.world.entity.Entity;

/**
 * Tells apart the ways an entity can arrive in a level, so {@link EntityJoinLevelEvent} carries the same
 * {@code loadedFromDisk} answer it does on Forge.
 *
 * <p>Fabric's ENTITY_LOAD callback fires for every arrival and says nothing about which kind it was. Only one
 * kind is a disk load -- a chunk bringing back its existing residents. Everything else is a fresh arrival: a
 * spawn, a teleport, a dimension change, a respawn, a worldgen mob. The mixins mark those paths as they run, and
 * anything that reaches ENTITY_LOAD unmarked came off disk.
 *
 * <p>That distinction is load-bearing: the emerald population cap and the obliteration check both ignore disk
 * loads, so a mob mis-flagged as one walks straight past them.
 *
 * <p>Kept per-thread, and re-entrant: an arrival handler that spawns something else nests cleanly.
 */
public final class EntityJoinTracking {
    private EntityJoinTracking() {}

    /** Entities whose join event has already been fired, cancellably, by the ServerLevel.addEntity mixin. */
    private static final ThreadLocal<Set<Entity>> ANNOUNCED =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    /** How deep we are inside an arrival path that is fresh but cannot be refused. */
    private static final ThreadLocal<int[]> FRESH_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public static void announced(Entity entity) {
        ANNOUNCED.get().add(entity);
    }

    public static void clearAnnounced(Entity entity) {
        ANNOUNCED.get().remove(entity);
    }

    public static boolean wasAnnounced(Entity entity) {
        return ANNOUNCED.get().contains(entity);
    }

    public static void pushFreshArrival() {
        FRESH_DEPTH.get()[0]++;
    }

    public static void popFreshArrival() {
        FRESH_DEPTH.get()[0]--;
    }

    /** True while a fresh, non-refusable arrival is in progress -- so NOT a disk load. */
    public static boolean insideFreshArrival() {
        return FRESH_DEPTH.get()[0] > 0;
    }
}
