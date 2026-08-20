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
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Village chest / barrel protection, with NO mixin. A "village chest" is a chest (incl. trapped chest) or barrel
 * whose block sits within {@link VillageEvents#NODE_DEFEND_RADIUS} of any tracked Village Brain Node.
 *
 * <ul>
 *   <li>PLACE: putting a chest/barrel inside a brain node's range donates it to the village (a thank-you note).</li>
 *   <li>OPEN: a player without Hero of the Village who opens a village chest is warned (server-side, throttled to
 *       once per 10s per player). The open is never cancelled -- they can look, but taking is theft.</li>
 *   <li>THEFT: snapshot the container's OCCUPIED SLOT COUNT when a non-hero opens it, and on close re-read the
 *       same source; every slot that emptied is a stack that left, and each one costs reputation (see
 *       {@code ReputationEvents.STEAL_STACK}) with a red broadcast. Heroes are exempt.</li>
 * </ul>
 *
 * <p>Slots rather than items, and that is the whole reason it is not a running total any more: the charge is
 * per STACK, so sixty-four cobblestone has to cost what one diamond costs. A partial take leaves the slot
 * occupied and is free, which is the right way round to be wrong -- the village notices things going, not
 * pilfering.
 *
 * Detection is snapshot-on-open + diff-on-close (no mixin). It guards against a null block entity, a chest
 * destroyed between open and close, and double chests (it snapshots and re-reads the SAME block entity).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageChestProtection {
    private VillageChestProtection() {}

    private static final int WARN_COOLDOWN_TICKS = 200; // 10s between warnings per player
    private static final Map<UUID, Long> lastWarn = new HashMap<>(); // player -> game time of last open warning

    /**
     * A non-hero player's open snapshot: which chest, and how many OCCUPIED SLOTS it held at open time.
     *
     * <p>Slots, not items, and that is the whole point of the change. Reputation charges
     * {@value ReputationEvents#STEAL_STACK} per STACK taken, so a stack of sixty-four cobblestone has to cost
     * the same as a single diamond -- which the old total-item count could not express: it saw 64 and 1. A
     * slot that emptied is a stack that left.
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
        if (!isVillageProperty(level, pos)) return;
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
        if (!isChestOrBarrel(be) || !isVillageProperty(level, pos)) return;
        if (trusted(player)) return; // Hero of the Village I and up may take what they like

        // Warn that taking from here costs standing -- throttled to once per 10s, and never cancelling the open.
        long now = level.getGameTime();
        Long last = lastWarn.get(player.getUUID());
        if (last == null || now - last >= WARN_COOLDOWN_TICKS) {
            lastWarn.put(player.getUUID(), now);
            player.displayClientMessage(Component.literal(
                    "This chest belongs to the village. Taking from it costs "
                    + (-ReputationEvents.STEAL_STACK) + " reputation a stack, until you are a Hero of the Village.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        // Snapshot the SAME block entity's occupied slots so the close handler can diff them for theft.
        openSnapshots.put(player.getUUID(), new Snapshot(pos.immutable(), occupiedSlots((Container) be)));
    }

    // --- CLOSE: diff the snapshot; a drop in total item count is theft -----------------------------

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Snapshot snap = openSnapshots.remove(player.getUUID()); // clear the snapshot either way
        if (snap == null) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (trusted(player)) return; // became a hero while the chest was open -> no longer a thief

        BlockEntity be = level.getBlockEntity(snap.pos());
        if (!isChestOrBarrel(be)) return; // chest destroyed / replaced between open and close -> can't diff
        int taken = snap.occupiedSlots() - occupiedSlots((Container) be);
        if (taken <= 0) return;
        ReputationData.get(level.getServer())
                .add(player, ReputationEvents.STEAL_STACK * taken, "stole " + taken + " stack");
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(player.getName().getString() + " is stealing from the village!")
                        .withStyle(ChatFormatting.RED), false);
    }

    // --- helpers -----------------------------------------------------------------------------------

    /** A chest (incl. trapped chest, which extends {@link ChestBlockEntity}) or a barrel. */
    private static boolean isChestOrBarrel(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
    }

    /** How many slots hold anything at all. One emptied slot is one stack gone; see {@link Snapshot}. */
    private static int occupiedSlots(Container container) {
        int used = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) used++;
        }
        return used;
    }

    /** True if a chest or barrel at {@code pos} belongs to a village. Used by the break-in penalty. */
    public static boolean isProtectedContainer(ServerLevel level, BlockPos pos) {
        return isChestOrBarrel(level.getBlockEntity(pos)) && isVillageProperty(level, pos);
    }

    /** True if {@code pos} is within {@link VillageEvents#NODE_DEFEND_RADIUS} of any tracked brain node here. */
    /**
     * Is this the village's property?
     *
     * <p>Two ways to qualify, and the second is why this exists. The radius around a brain node covers the
     * old, compact village; it does NOT cover a grand house or anything in the stage-3 expansion ring, which
     * are built far past {@code NODE_DEFEND_RADIUS} -- so their chests were free to loot, which is exactly
     * what showed up when the grand houses landed. A container inside a building the village RAISED is the
     * village's whether or not it happens to sit near the node, and StructureZones is the register of every
     * such building (it is also a chunk-bucketed lookup, so this costs nothing).
     */
    public static boolean isVillageProperty(ServerLevel level, BlockPos pos) {
        return withinBrainNodeRange(level, pos)
                || com.example.villagefightsback.infection.StructureZones.get(level).contains(pos);
    }

    public static boolean withinBrainNodeRange(ServerLevel level, BlockPos pos) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        double r2 = VillageEvents.NODE_DEFEND_RADIUS * VillageEvents.NODE_DEFEND_RADIUS;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(level.dimension()) && gp.pos().distSqr(pos) <= r2) return true;
        }
        return false;
    }

    /**
     * Trusted with the village's stores: Hero of the Village, any level.
     *
     * <p>Reads the STATUS rather than the reputation number on purpose. The status is a cached copy of
     * "is this reputation at or above 100" that {@link ReputationData} keeps in step, and reading it here
     * keeps a chest interaction from touching saved data.
     */
    private static boolean trusted(Player player) {
        return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }
}
