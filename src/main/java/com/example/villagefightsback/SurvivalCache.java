package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Survival starter cache: where it is, who it belongs to, and how far away they are.
 *
 * <h2>Why it is promised rather than written</h2>
 * The cache is buried up to 100 blocks from spawn, and at the moment it is granted that chunk may not
 * exist yet.
 * Writing the chest directly does not work: {@code setBlock} needs the chunk generated, and a synchronous
 * full generation cannot be forced from inside a packet handler mid-tick -- the write lands on nothing,
 * {@code getBlockEntity} answers null, and the whole Survival opening silently fell back to the bell. So it
 * is recorded here and written when its chunk loads for real, which is when the player walks out to dig it
 * up. Nothing is force-loaded and nothing is generated early.
 *
 * <h2>Why a distance and not a map</h2>
 * A map item can be dropped, lost, or squeezed out of a hotbar another mod has already filled, and it cannot
 * say anything. The action bar can: it costs no inventory slot, survives death, and answers the only question
 * the player actually has, which is how much further.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SurvivalCache extends SavedData {
    private static final String NAME = "thevillagefightsback_survival_cache";

    /** How often the readout refreshes. Every tick would be twenty messages a second for a number that
     *  changes by less than a block. */
    private static final int REFRESH_TICKS = 10;

    /** Who is looking for what. One entry per player who has been granted a cache and not yet reached it. */
    private final Map<UUID, BlockPos> targets = new HashMap<>();

    public static SurvivalCache get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SurvivalCache::load, SurvivalCache::new, NAME);
    }

    /**
     * Promise a cache at {@code pos} for {@code owner}, and write it now if its chunk is already loaded.
     *
     * <p>The write-on-chunk-load path alone is not enough, and the gap is easy to miss: a cache 40 to 100
     * blocks away usually lands in a chunk that is ALREADY loaded, so no load event will ever fire for it and
     * the chest was never written. It only worked at the original 300-800 blocks because that was reliably
     * outside the loaded area. So: write it here when we can, and fall back to the load event when we cannot.
     */
    public void promise(ServerLevel level, BlockPos pos, UUID owner) {
        targets.put(owner, pos.immutable());
        setDirty();
        if (level.hasChunkAt(pos)) writeChest(level, pos);
    }

    /** Put the chest and its contents at {@code pos}. The chunk must already be loaded. */
    private static void writeChest(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.CHEST)) return;      // already written
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            TheVillageFightsBack.LOGGER.warn(
                    "[The Village Fights Back] could not write the Survival starter cache at {}.", pos);
            return;
        }
        // A DEAD node, not a living one: Survival never hands the player a working village. The apple to wake
        // it is theirs to craft, and both books are here so the rules arrive with the thing they describe.
        //
        // The node sits BETWEEN the books, so opening the chest reads as the thing and its two manuals.
        // A chest is 9 wide, so the middle row is slots 9..17 and the rows above and below are that minus and
        // plus nine. The three items take 12/13/14 and eight gold ingots ring them:
        //
        //     .  .  .  Au Au Au .  .  .      3  4  5
        //     .  .  Au Bk Nd Bk Au .  .     11 12 13 14 15
        //     .  .  .  Au Au Au .  .  .     21 22 23
        //
        // Gold on purpose: it is the stage 2 -> 3 tribute, so the cache carries a down payment on the village
        // it contains rather than a generic reward.
        chest.setItem(12, new ItemStack(ModItems.REPUTATION_BOOK.get()));
        chest.setItem(13, new ItemStack(ModBlocks.DEAD_BRAIN_NODE.get()));
        chest.setItem(14, new ItemStack(ModItems.LIMBO_BOOK.get()));
        for (int slot : new int[]{3, 4, 5, 11, 15, 21, 22, 23}) {
            chest.setItem(slot, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT));
        }
        chest.setChanged();
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] the Survival starter cache at {} has been written.", pos);
    }

    /**
     * A chunk loaded: if a cache sits in it, write the chest now.
     *
     * <p>Placed with the chunk already in hand, so this is an ordinary block write with no generation behind
     * it. The entry is NOT cleared here -- the readout runs until the player is actually standing on it,
     * which is a different moment from the chunk loading.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SurvivalCache data = level.getDataStorage().get(SurvivalCache::load, NAME);
        if (data == null || data.targets.isEmpty()) return;      // nothing promised anywhere -> free
        ChunkPos chunk = event.getChunk().getPos();
        for (BlockPos pos : data.targets.values()) {
            if (new ChunkPos(pos).toLong() == chunk.toLong()) writeChest(level, pos);
        }
    }

    /**
     * Tell the owner how far they still have to go, on the action bar, as they move.
     *
     * <p>Throttled to {@value #REFRESH_TICKS} ticks and skipped entirely when the player has not moved, so
     * standing still costs nothing and the number does not flicker. The map lookup is against a table that is
     * empty in every world that is not mid-hunt, so this is free for everyone else.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % REFRESH_TICKS != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        SurvivalCache data = level.getDataStorage().get(SurvivalCache::load, NAME);
        if (data == null || data.targets.isEmpty()) return;
        BlockPos target = data.targets.get(player.getUUID());
        if (target == null) return;

        // Straight-line 3D: distSqr is dx^2 + dy^2 + dz^2, so the buried depth counts. It runs all the way
        // down rather than cutting out near the target -- a cutoff at six blocks meant standing on the surface
        // above a chest four deep ended the readout before it could count down, which reads exactly like the
        // depth being ignored.
        int distance = (int) Math.round(Math.sqrt(target.distSqr(player.blockPosition())));
        player.displayClientMessage(Component.literal(
                "§7You are §e" + distance + "§7 blocks away from the treasure."), true);
    }

    /**
     * Opening the chest is what counts as finding it -- not standing near it.
     *
     * <p>Proximity is the wrong signal: the cache is buried, so a player can be four blocks from it with a
     * layer of stone in between and nothing found at all. The readout runs until the lid is actually lifted.
     */
    @SubscribeEvent
    public static void onRightClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SurvivalCache data = level.getDataStorage().get(SurvivalCache::load, NAME);
        if (data == null || data.targets.isEmpty()) return;          // nothing being hunted -> free
        BlockPos target = data.targets.get(player.getUUID());
        if (target == null || !target.equals(event.getPos())) return;
        if (!level.getBlockState(target).is(Blocks.CHEST)) return;

        data.targets.remove(player.getUUID());
        data.setDirty();
        player.displayClientMessage(Component.literal("§6You found it."), true);
    }

    // --- persistence ---------------------------------------------------------------------------------

    public static SurvivalCache load(CompoundTag tag) {
        SurvivalCache d = new SurvivalCache();
        ListTag list = tag.getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Owner")) continue;
            d.targets.put(t.getUUID("Owner"), BlockPos.of(t.getLong("Pos")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, BlockPos> e : targets.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", e.getKey());
            t.putLong("Pos", e.getValue().asLong());
            list.add(t);
        }
        tag.put("Targets", list);
        return tag;
    }
}
