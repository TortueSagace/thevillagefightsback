package com.example.villagergunners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Tracks placed {@link CheapScaffoldBlock}s and auto-breaks them ~30s after placement, dropping nothing.
 * For efficiency, scaffolds placed close together in time share ONE timer: a placement
 * joins the current "open" batch (within {@link #GROUP_WINDOW} ticks of its start) instead of getting its
 * own counter, so a whole pillar of scaffolds expires together. Persisted so timers survive a save/reload.
 */
public class CheapScaffoldManager extends SavedData {
    private static final String NAME = "gobacktoyourworld_cheap_scaffold";

    public static final long LIFETIME = 600L;     // ~30s before a batch auto-breaks
    private static final long GROUP_WINDOW = 60L; // placements within this of a batch's start share its timer

    private static final class Batch {
        ResourceKey<Level> dim;
        long expiry;
        long openUntil;
        final List<Long> positions = new ArrayList<>();
    }

    private final List<Batch> batches = new ArrayList<>();
    // Transient: the latest still-open batch per dimension, for grouping new placements. Not persisted.
    private final transient Map<ResourceKey<Level>, Batch> open = new HashMap<>();

    public static CheapScaffoldManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CheapScaffoldManager::load, CheapScaffoldManager::new, NAME);
    }

    /** Register a freshly placed cheap scaffold onto the current (or a new) shared timer. */
    public static void track(ServerLevel level, BlockPos pos) {
        get(level.getServer()).addPos(level.dimension(), pos, level.getGameTime());
    }

    private void addPos(ResourceKey<Level> dim, BlockPos pos, long now) {
        Batch b = open.get(dim);
        if (b == null || now > b.openUntil) {
            b = new Batch();
            b.dim = dim;
            b.expiry = now + LIFETIME;
            b.openUntil = now + GROUP_WINDOW;
            batches.add(b);
            open.put(dim, b);
        }
        b.positions.add(pos.asLong());
        setDirty();
    }

    /** Break every cheap scaffold whose batch timer has elapsed (dropping nothing). Cheap to run. */
    public static void tick(MinecraftServer server) {
        get(server).expire(server);
    }

    private void expire(MinecraftServer server) {
        if (batches.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Iterator<Batch> it = batches.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Batch b = it.next();
            if (now < b.expiry) continue;
            ServerLevel level = server.getLevel(b.dim);
            if (level != null) {
                for (long l : b.positions) {
                    BlockPos p = BlockPos.of(l);
                    if (level.hasChunkAt(p) && level.getBlockState(p).is(ModBlocks.CHEAP_SCAFFOLD.get())) {
                        level.removeBlock(p, false); // auto-break (cooldown) drops nothing
                    }
                }
            }
            it.remove();
            open.values().remove(b);
            changed = true;
        }
        if (changed) setDirty();
    }

    // --- persistence ---

    public static CheapScaffoldManager load(CompoundTag tag) {
        CheapScaffoldManager d = new CheapScaffoldManager();
        ListTag list = tag.getList("batches", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Batch b = new Batch();
            b.dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("dim")));
            b.expiry = c.getLong("expiry");
            b.openUntil = c.getLong("openUntil");
            for (long l : c.getLongArray("pos")) b.positions.add(l);
            d.batches.add(b);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Batch b : batches) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", b.dim.location().toString());
            c.putLong("expiry", b.expiry);
            c.putLong("openUntil", b.openUntil);
            long[] arr = new long[b.positions.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = b.positions.get(i);
            c.putLongArray("pos", arr);
            list.add(c);
        }
        tag.put("batches", list);
        return tag;
    }
}
