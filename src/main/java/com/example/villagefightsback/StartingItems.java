package com.example.villagefightsback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.example.villagefightsback.network.ModNetwork;

/**
 * Gives every player a single Bell the first time they ever join a world -- the seed for founding their own
 * Village Brain Node (gated by {@link Config#playerGivenBellAtSpawn()}). Tracked with a flag in the player's
 * PERSISTED data so it is granted exactly once and survives death, dimension changes and relogs.
 *
 * <p>Also enforces {@link Config#playerIsHostile()}: while that switch is on, every player is kept permanently
 * marked as an Enemy of the Village and can never hold Hero of the Village -- re-applied on login and policed by
 * a cheap once-a-second sweep (so milk, expiry or a donation can never lift it).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class StartingItems {
    private StartingItems() {}

    private static final String NBT_GOT_BELL = "vgun_got_starter_bell";
    private static final String NBT_GOT_BOOK = "vgun_got_reputation_book";

    /**
     * Hand {@code stack} over once ever, remembered under {@code flag}.
     *
     * <p>The flag lives in {@code Player.PERSISTED_NBT_TAG}, the one bag of player data that survives death, a
     * dimension change and a relog -- so "once" means once, not once per life.
     *
     * <p>Unlike the bell, the Reputation Book goes out on EVERY difficulty. The bell is the means to found a
     * village and Hostile and Nightmare hand you one already built, so offering it there reads backwards. The
     * book is the rules, and the rules apply to everyone -- it just says something very different on the
     * difficulties where your standing is not up for negotiation.
     */
    private static void giveOnce(ServerPlayer player, String flag, ItemStack stack) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(flag)) return;
        persisted.putBoolean(flag, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // On a BRAND NEW world the creation screens have not been answered yet -- the player is already in the
        // world while they are up -- so at login we do not yet know the experience or the difficulty, and
        // handing out a bell here would give a Survival player the very thing Survival replaces. Wait: the
        // difficulty packet calls grantStarterKit the moment the answers land. Established worlds fall through
        // immediately, because setup is not pending for them.
        WorldModeData mode = WorldModeData.getExisting(player.getServer());
        if (mode != null && mode.isSetupPending()) return;
        grantStarterKit(player);
    }

    /**
     * The opening kit, once the world knows what kind of world it is.
     *
     * <p>Creative gets what it always got: the Reputation Book, and a bell to found a village with. Survival
     * gets a map instead, with the book buried in the cache at the other end of it -- so the rules and the
     * means arrive together, after the walk.
     */
    public static void grantStarterKit(ServerPlayer player) {
        boolean survival = WorldModeData.isSurvival(player.getServer());

        if (!survival) {
            giveOnce(player, NBT_GOT_BOOK, new ItemStack(ModItems.REPUTATION_BOOK.get()));
        }

        if (!Config.playerGivenBellAtSpawn()) return;               // bell handout disabled by config
        // Hostile and Nightmare start you with a village instead of the means to found one (see HostileStart).
        // The bell is a gift to someone the village has not judged yet, and on those difficulties it already has.
        if (Difficulty.current().placesBrainNodeAtSpawn()) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(NBT_GOT_BELL)) return;             // already received the starter bell
        persisted.putBoolean(NBT_GOT_BELL, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        // Survival: a map to a buried cache instead of a bell. If nowhere workable turns up the bell is the
        // fallback, so the opening can never leave a player with nothing to found a village with.
        if (survival && SurvivalStart.give(player)) {
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] Survival starter map given to {}.",
                    player.getGameProfile().getName());
            return;
        }

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
        ModNetwork.sendTo(player,
                new com.example.villagefightsback.network.SpawnHintPacket(0));   // 0 ticks: hide it now
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
    public static void onBellPlaced(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(net.minecraft.world.level.block.Blocks.BELL)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pendingHint.remove(player.getUUID());
        // NeoForge 1.21 replaced SimpleChannel + PacketDistributor.PLAYER.with(...) with a payload sent
        // straight at one player; ModNetwork.sendTo is this branch's wrapper for it.
        ModNetwork.sendTo(player,
                new com.example.villagefightsback.network.SpawnHintPacket(0));   // 0 ticks: hide it now
    }

    /** Once a second, while player_is_hostile is on, keep every player an Enemy of the Village and never a Hero. */
    @SubscribeEvent
    // PlayerTickEvent.Post is 1.21's split-out END phase, so main's `phase != END -> return` is implicit.
    // NOTE the ordering main settled on: the bell hint is released BEFORE the playerIsHostile() early-out,
    // because the hint has nothing to do with that switch and was previously swallowed whenever it was off.
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // The queued starter-bell hint, released the moment the player takes their first steps.
        net.minecraft.world.phys.Vec3 from = pendingHint.get(player.getUUID());
        if (from != null && player.distanceToSqr(from.x, from.y, from.z) > MOVE_TRIGGER_SQR) {
            pendingHint.remove(player.getUUID());
            ModNetwork.sendTo(player,
                    new com.example.villagefightsback.network.SpawnHintPacket(HINT_TICKS));
            TheVillageFightsBack.LOGGER.info("[The Village Fights Back] bell hint sent to {} (first movement).",
                    player.getGameProfile().getName());
        }

        if (!Config.playerIsHostile()) return;
        if (player.tickCount % 20 != 0) return;                     // cheap: ~1/s
        enforceHostile(player);
    }

    /** Strip Hero of the Village and (re)apply a permanent Enemy of the Village mark. */
    private static void enforceHostile(ServerPlayer player) {
        if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        }
        var enemy = ModEffects.ENEMY_OF_THE_VILLAGE;
        if (!player.hasEffect(enemy)) {
            // Permanent, no particles (visible=false), shows icon. Milk-proofing lives on the effect itself:
            // EnemyOfVillageEffect#fillEffectCures leaves the instance's cure set EMPTY, which is 1.21's
            // equivalent of main's setCurativeItems(new ArrayList<>()).
            MobEffectInstance inst = new MobEffectInstance(enemy, Integer.MAX_VALUE, 0, false, false, true);
            player.addEffect(inst);
        }
    }
}
