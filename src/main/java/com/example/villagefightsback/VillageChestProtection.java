package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Village chest / barrel protection, with NO mixin. A "village chest" is a chest (incl. trapped chest) or barrel
 * whose block sits within {@link VillageEvents#NODE_DEFEND_RADIUS} of any tracked Village Brain Node.
 *
 * <ul>
 *   <li>PLACE: putting a chest/barrel inside a brain node's range donates it to the village (a thank-you note).</li>
 *   <li>OPEN: a player without Hero of the Village who opens a village chest is warned (server-side, throttled to
 *       once per 10s per player). The open is never cancelled -- they can look, but taking is theft.</li>
 *   <li>THEFT: snapshot the container's total item count when a non-hero opens it, and on close re-read the same
 *       source; if the total dropped, the player took something -> brand them a thief (Enemy of the Village for
 *       1 min) with a red broadcast (see {@link VillageEvents#markThief}). Heroes are exempt.</li>
 * </ul>
 *
 * Detection is snapshot-on-open + diff-on-close (no mixin). It guards against a null block entity, a chest
 * destroyed between open and close, and double chests (it snapshots and re-reads the SAME block entity).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class VillageChestProtection {
    private VillageChestProtection() {}

    private static final int WARN_COOLDOWN_TICKS = 200; // 10s between warnings per player
    private static final Map<UUID, Long> lastWarn = new HashMap<>(); // player -> game time of last open warning

    /** A non-hero player's open snapshot: which chest, and how many items it held at open time. */
    /**
     * A non-hero player's open snapshot: which chest, and how many OCCUPIED SLOTS it held at open time.
     *
     * <p>Slots, not items, and that is the whole point. Reputation charges per STACK taken, so a stack of
     * sixty-four cobblestone has to cost the same as a single diamond -- which a running item total could not
     * express: it saw 64 and 1. A slot that emptied is a stack that left.
     *
     * <p>It undercounts a partial take (grabbing 30 of 64 leaves the slot occupied and costs nothing), and
     * that is the right way round to be wrong: the village notices things GOING, not pilfering.
     */
    private record Snapshot(BlockPos pos, int occupiedSlots) {}
    private static final Map<UUID, Snapshot> openSnapshots = new HashMap<>(); // player -> what they last opened

    // --- PLACE: a chest/barrel placed inside a brain node's range joins the village ----------------

    @SubscribeEvent
    public static void onChestPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!isChestOrBarrel(level.getBlockEntity(pos))) return;
        if (!withinBrainNodeRange(level, pos)) return;
        player.displayClientMessage(
                Component.literal("Thanks! This chest now belongs to the village."), false);
    }

    // --- OPEN: warn a non-hero (no cancel), and snapshot the contents for theft detection ----------

    @SubscribeEvent
    public static void onChestRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!isChestOrBarrel(be) || !withinBrainNodeRange(level, pos)) return;
        if (hasHero(player)) return; // heroes (and the village's own villagers, which aren't players) are trusted

        // Warn that taking from here is forbidden -- throttled to once per 10s, and never cancelling the open.
        long now = level.getGameTime();
        Long last = lastWarn.get(player.getUUID());
        if (last == null || now - last >= WARN_COOLDOWN_TICKS) {
            lastWarn.put(player.getUUID(), now);
            player.displayClientMessage(Component.literal(
                    "You don't have the right to take objects from here: you are not a hero of the village. "
                    + "Feed the brain with emeralds to be temporary allowed to.").withStyle(ChatFormatting.YELLOW),
                    false);
        }

        // Snapshot the SAME block entity's total item count so the close handler can diff it for theft.
        openSnapshots.put(player.getUUID(), new Snapshot(pos.immutable(), occupiedSlots((Container) be)));
    }

    // --- CLOSE: diff the snapshot; every slot that emptied is a stack that left ---------------------

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Snapshot snap = openSnapshots.remove(player.getUUID()); // clear the snapshot either way
        if (snap == null) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (hasHero(player)) return; // gained hero while the chest was open -> no longer a thief

        BlockEntity be = level.getBlockEntity(snap.pos());
        if (!isChestOrBarrel(be)) return; // chest destroyed / replaced between open and close -> can't diff
        int taken = snap.occupiedSlots() - occupiedSlots((Container) be);
        if (taken > 0) {
            ReputationData.get(level.getServer())
                    .add(player, ReputationEvents.STEAL_STACK * taken, "stole " + taken + " stack");
            level.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            player.getName().getString() + " is stealing from the village!")
                            .withStyle(net.minecraft.ChatFormatting.RED), false);
        }
    }

    // --- helpers -----------------------------------------------------------------------------------

    /** True if a chest or barrel at {@code pos} belongs to a village. Used by the break-in penalty. */
    public static boolean isProtectedContainer(ServerLevel level, BlockPos pos) {
        return isChestOrBarrel(level.getBlockEntity(pos)) && withinBrainNodeRange(level, pos);
    }

    /** A chest (incl. trapped chest, which extends {@link ChestBlockEntity}) or a barrel. */
    private static boolean isChestOrBarrel(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
    }

    /** Sum of every stack count in {@code container} (a single block entity's own slots). */
    private static int occupiedSlots(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) total += stack.getCount();
        }
        return total;
    }

    /** True if {@code pos} is within {@link VillageEvents#NODE_DEFEND_RADIUS} of any tracked brain node here. */
    public static boolean withinBrainNodeRange(ServerLevel level, BlockPos pos) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        double r2 = VillageEvents.NODE_DEFEND_RADIUS * VillageEvents.NODE_DEFEND_RADIUS;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(level.dimension()) && gp.pos().distSqr(pos) <= r2) return true;
        }
        return false;
    }

    private static boolean hasHero(Player player) {
        return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }
}
