package com.example.villagefightsback;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The "freed soul gravel" rule (R3). A {@link LimboSoulGravelItem} loose in the world frees its banished
 * creature -- the creature returns at the item's location -- once it has been loose for {@value #FREE_DELAY}
 * ticks (3 s), OR sooner if it is DESTROYED while loose (burned in lava, fell out of the world). Two cases the
 * tick loop carefully does NOT treat as "destroyed": the item being UNLOADED with its chunk (it still exists,
 * just sleeping) and the item being PICKED UP / stored (then the creature stays banished). It distinguishes
 * destruction from a pickup/store by the removal reason plus whether the same soul gravel is now carried.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboGravelItemEvents {
    private LimboGravelItemEvents() {}

    private static final int FREE_DELAY = 60; // 3s loose -> the creature is freed

    private record Tracked(ItemEntity item, UUID token) {}
    private static final Map<UUID, Tracked> TRACKED = new ConcurrentHashMap<>();

    /** Start watching any loose soul-gravel item the moment it appears (silk drop, toss, chest break, death). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity ie)) return;
        if (!LimboSoulGravelItem.isSoulGravel(ie.getItem())) return;
        UUID token = LimboSoulGravelItem.token(ie.getItem());
        if (token != null) TRACKED.put(ie.getUUID(), new Tracked(ie, token));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Tracked>> it = TRACKED.entrySet().iterator(); it.hasNext(); ) {
            Tracked t = it.next().getValue();
            ItemEntity ie = t.item();
            if (ie.isRemoved()) {
                it.remove();
                Entity.RemovalReason reason = ie.getRemovalReason();
                // UNLOADED_* / CHANGED_DIMENSION -> the item still exists (sleeping / moved), NOT destroyed: keep
                // the creature banished (it re-tracks via onJoin when the chunk reloads).
                if (reason == null || !reason.shouldDestroy()) continue;
                // KILLED / DISCARDED -> destroyed in lava/void, OR picked up / hopper'd into storage. Free only
                // if no player is now carrying this soul gravel (a successful pickup keeps the creature banished).
                if (ie.getLevel() instanceof ServerLevel sl && !LimboManager.isTokenCarried(sl.getServer(), t.token())) {
                    freeAt(sl, ie, t.token());
                }
            } else if (ie.getAge() >= FREE_DELAY) {
                if (ie.getLevel() instanceof ServerLevel sl) freeAt(sl, ie, t.token()); // loose 3s -> free here
                ie.discard();
                it.remove();
            }
        }
    }

    private static void freeAt(ServerLevel sl, ItemEntity ie, UUID token) {
        double x = ie.getX(), z = ie.getZ();
        double y = ie.getY();
        if (y < sl.getMinBuildHeight() + 1) {
            // item fell into the void -> return the creature on the surface above, not in the void
            y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        }
        LimboManager.freeSoulGravel(sl, x, y, z, token);
    }
}
