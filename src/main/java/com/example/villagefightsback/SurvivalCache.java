package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Survival starter caches that have been promised but not yet written.
 *
 * <p>The cache is buried 300-800 blocks from spawn, and at the moment the map is handed out that chunk does
 * not exist. Writing the chest there directly does not work: {@code setBlock} needs the chunk generated, and
 * a synchronous full generation cannot be forced from inside a packet handler mid-tick -- the write lands on
 * nothing, {@code getBlockEntity} answers null, and the whole Survival opening silently fell back to the
 * bell. That is exactly the bug this class exists to remove.
 *
 * <p>So the chest is PROMISED here and written when its chunk loads for real, which is when the player walks
 * out to dig it up. Nothing is force-loaded and nothing is generated early.
 *
 * <p>Cost is a hash lookup per chunk load, against a map that holds one entry per player who has ever been
 * given a map and is empty in every established world.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public class SurvivalCache extends SavedData {
    private static final String NAME = "thevillagefightsback_survival_cache";

    /** chunk key ({@link ChunkPos#toLong}) -> where in it the chest goes. */
    private final Map<Long, BlockPos> pending = new HashMap<>();

    public static SurvivalCache get(ServerLevel level) {
        // 1.21: DataStorage takes a SavedData.Factory and threads a HolderLookup.Provider through both halves.
        // Nothing here is registry data, so the provider is unused.
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SurvivalCache::new, SurvivalCache::load), NAME);
    }

    /** Promise a cache at {@code pos}; it is written the next time that chunk loads. */
    public void promise(BlockPos pos) {
        pending.put(new ChunkPos(pos).toLong(), pos.immutable());
        setDirty();
    }

    /**
     * A chunk loaded: if a cache was promised in it, write the chest now.
     *
     * <p>Placed with the chunk already in hand, so this is an ordinary block write with no generation behind
     * it. The promise is cleared whether or not the write succeeds -- a cache that cannot be written at its
     * own loaded chunk will not succeed on the next visit either, and retrying forever would only mean a hash
     * lookup per chunk load for the rest of the world's life.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SurvivalCache data = level.getDataStorage().get(
                new SavedData.Factory<>(SurvivalCache::new, SurvivalCache::load), NAME);
        if (data == null || data.pending.isEmpty()) return;      // nothing promised anywhere -> free
        BlockPos pos = data.pending.remove(event.getChunk().getPos().toLong());
        if (pos == null) return;
        data.setDirty();
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            // A DEAD node, not a living one: Survival never hands the player a working village. The apple to
            // wake it is theirs to craft, and both books are here so the rules arrive with the thing they
            // describe.
            chest.setItem(12, new ItemStack(ModBlocks.DEAD_BRAIN_NODE.get()));
            chest.setItem(13, new ItemStack(ModItems.REPUTATION_BOOK.get()));
            chest.setItem(14, new ItemStack(ModItems.LIMBO_BOOK.get()));
            chest.setChanged();
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] the Survival starter cache at {} has been written.", pos);
        } else {
            TheVillageFightsBack.LOGGER.warn(
                    "[The Village Fights Back] could not write the Survival starter cache at {}.", pos);
        }
    }

    // --- persistence ---------------------------------------------------------------------------------

    public static SurvivalCache load(CompoundTag tag, HolderLookup.Provider registries) {
        SurvivalCache d = new SurvivalCache();
        ListTag list = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BlockPos p = BlockPos.of(list.getCompound(i).getLong("Pos"));
            d.pending.put(new ChunkPos(p).toLong(), p);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos p : pending.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", p.asLong());
            list.add(t);
        }
        tag.put("Pending", list);
        return tag;
    }
}
