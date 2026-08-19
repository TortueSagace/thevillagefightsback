package com.example.villagefightsback;

import com.example.villagefightsback.platform.PersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.event.PlayerEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

import com.example.villagefightsback.network.ModNetwork;

/**
 * Gives every player a single Bell the first time they ever join a world -- the seed for founding their own
 * Village Brain Node (gated by {@link Config#playerGivenBellAtSpawn()}). Tracked with a flag in the player's
 * PERSISTED data so it is granted exactly once and survives death, dimension changes and relogs.
 *
 * <p>It used to police {@link Config#playerIsHostile()} here too, stamping a permanent Enemy of the Village on
 * every player once a second. Standing is derived from reputation now, so that switch is honoured where the
 * number is decided instead ({@code ReputationData.standing()}), and nothing here touches a status effect.
 */
public final class StartingItems {
    private StartingItems() {}

    private static final String NBT_GOT_BELL = "vgun_got_starter_bell";
    private static final String NBT_GOT_BOOK = "vgun_got_reputation_book";

    /**
     * Hand {@code stack} over once ever, remembered under {@code flag}.
     *
     * <p>The flag lives in {@code PersistentData.PERSISTED_NBT_TAG}, which is the one bag of player data that survives
     * death, a dimension change and a relog -- so "once" means once, not once per life.
     *
     * <p>Unlike the bell, the Reputation Book goes out on EVERY difficulty. The bell is the means to found a
     * village and Hostile and Nightmare hand you one already built, so offering it there reads backwards. The
     * book is the rules, and the rules apply to everyone -- it just says something very different on the
     * difficulties where your standing is not up for negotiation.
     */
    private static void giveOnce(ServerPlayer player, String flag, ItemStack stack) {
        CompoundTag persisted = PersistentData.of(player).getCompound(PersistentData.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(flag)) return;
        persisted.putBoolean(flag, true);
        PersistentData.of(player).put(PersistentData.PERSISTED_NBT_TAG, persisted);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        giveOnce(player, NBT_GOT_BOOK, new ItemStack(ModItems.REPUTATION_BOOK.get()));

        if (!Config.playerGivenBellAtSpawn()) return;               // bell handout disabled by config
        // Hostile and Nightmare start you with a village instead of the means to found one (see HostileStart).
        // The bell is a gift to someone the village has not judged yet, and on those difficulties it already has.
        if (Difficulty.current().placesBrainNodeAtSpawn()) return;
        CompoundTag persisted = PersistentData.of(player).getCompound(PersistentData.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(NBT_GOT_BELL)) return;             // already received the starter bell
        persisted.putBoolean(NBT_GOT_BELL, true);
        PersistentData.of(player).put(PersistentData.PERSISTED_NBT_TAG, persisted);

        ItemStack bell = new ItemStack(Items.BELL);
        if (!player.getInventory().add(bell)) player.drop(bell, false); // drop at feet if the inventory is full

        // Spawned away from any village: nudge them about what the bell is FOR. Queued rather than sent now,
        // because at login the client is usually still on the loading screen and would never see it.
        boolean away = !inVillage(player);
        if (away) pendingHint.put(player.getUUID(), player.position());
        TheVillageFightsBack.LOGGER.info("[The Village Fights Back] starter bell given to {}; {}.",
                player.getGameProfile().getName(),
                away ? "spawned away from a village, so the bell hint is queued"
                     : "spawned in a village, so no bell hint");
    }

    /**
     * Where each waiting player stood when they were handed the bell. The hint is held until they MOVE.
     *
     * <p>A timer was tried first and did not work: on a new world the player spends an unknown stretch on the
     * game-mode and send-off screens, so any fixed delay either fires behind them or guesses wrong. Movement is
     * the one signal that means the player is genuinely in the world and in control -- you cannot walk while a
     * screen is open -- so it needs no guess about how long they take to click through.
     *
     * <p>Session-only: an unsent hint is not worth persisting.
     */
    /**
     * Call off the bell hint for {@code player}, queued or already on screen.
     *
     * <p>The hint is queued at LOGIN, before the difficulty is known, so a Hostile or Nightmare world queues
     * one and then confiscates the very bell it was advising the player to place. Advice about an item you no
     * longer have is worse than no advice.
     */
    public static void cancelHint(net.minecraft.server.level.ServerPlayer player) {
        pendingHint.remove(player.getUUID());
        com.example.villagefightsback.network.ModNetwork.sendTo(player, new com.example.villagefightsback.network.SpawnHintPacket(0));   // 0 ticks: hide it now
    }

    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> pendingHint =
            new java.util.HashMap<>();
    /** How far the player must move from the spot they spawned on before the hint is shown. */
    private static final double MOVE_TRIGGER_SQR = 1.0;
    private static final int HINT_TICKS = 100;      // 5s on screen, then it fades out

    /**
     * Is the player standing in a village? True inside a generated village's structure, or near a bell (the
     * village meeting point), which also covers a player-built or modded village that vanilla never generated.
     */
    private static boolean inVillage(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        if (level.structureManager()
                .getStructureWithPieceAt(pos, net.minecraft.tags.StructureTags.VILLAGE).isValid()) {
            return true;
        }
        return level.getPoiManager().getInSquare(
                        h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), pos, 48,
                        net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .findAny().isPresent();
    }

    /** Forget a queued hint for a player who left before it fired, so the map cannot accumulate stale entries. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingHint.remove(event.getEntity().getUUID());
    }

    /**
     * The moment the player puts the bell down, the hint has served its purpose and is dismissed early, rather
     * than hanging on screen telling them to do the thing they have just done.
     *
     * <p>Also drops a hint that was still waiting to be sent, for a player who places the bell without having
     * walked anywhere first.
     */
    @SubscribeEvent
    public static void onBellPlaced(com.example.villagefightsback.platform.event.BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(net.minecraft.world.level.block.Blocks.BELL)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pendingHint.remove(player.getUUID());
        com.example.villagefightsback.network.ModNetwork.sendTo(player, new com.example.villagefightsback.network.SpawnHintPacket(0));   // 0 ticks: hide it now
    }

    /** Releases the queued starter-bell hint the moment the player takes their first steps. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // The queued starter-bell hint, released the moment the player takes their first steps.
        net.minecraft.world.phys.Vec3 from = pendingHint.get(player.getUUID());
        if (from != null && player.distanceToSqr(from.x, from.y, from.z) > MOVE_TRIGGER_SQR) {
            pendingHint.remove(player.getUUID());
            com.example.villagefightsback.network.ModNetwork.sendTo(player, new com.example.villagefightsback.network.SpawnHintPacket(HINT_TICKS));
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] bell hint sent to {} (first movement).",
                    player.getGameProfile().getName());
        }

    }

    /*
     * The permanent Enemy of the Village that used to be stamped on here, once a second, while
     * `player_is_hostile` was on is gone. Not the setting -- the stamping. Standing is derived from a
     * reputation number now and nothing else may grant it, so the switch says the same thing where the
     * number is decided instead: see ReputationData.standing(), which pins a hostile world's reputation
     * exactly where the Hostile difficulty pins it.
     */
}
