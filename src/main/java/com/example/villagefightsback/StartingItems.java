package com.example.villagefightsback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StartingItems {
    private StartingItems() {}

    private static final String NBT_GOT_BELL = "vgun_got_starter_bell";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (Config.playerIsHostile()) enforceHostile(player);

        if (!Config.playerGivenBellAtSpawn()) return;               // bell handout disabled by config
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(NBT_GOT_BELL)) return;             // already received the starter bell
        persisted.putBoolean(NBT_GOT_BELL, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        ItemStack bell = new ItemStack(Items.BELL);
        if (!player.getInventory().add(bell)) player.drop(bell, false); // drop at feet if the inventory is full

        // Spawned away from any village: nudge them about what the bell is FOR. Queued rather than sent now,
        // because at login the client is usually still on the loading screen and would never see it.
        if (!inVillage(player)) pendingHint.put(player.getUUID(), HINT_DELAY_TICKS);
    }

    /** Ticks left before each player's starter-bell hint is sent. Session-only: an unsent hint is not worth saving. */
    private static final java.util.Map<java.util.UUID, Integer> pendingHint = new java.util.HashMap<>();
    private static final int HINT_DELAY_TICKS = 40; // ~2s after joining, so it lands after the loading screen
    private static final int HINT_TICKS = 60;       // 3s on screen, then it fades out

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

    /** Once a second, while player_is_hostile is on, keep every player an Enemy of the Village and never a Hero. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // The queued starter-bell hint, counted down here so it lands once the player is actually in the world.
        Integer left = pendingHint.get(player.getUUID());
        if (left != null) {
            if (left <= 1) {
                pendingHint.remove(player.getUUID());
                ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new com.example.villagefightsback.network.SpawnHintPacket(HINT_TICKS));
            } else {
                pendingHint.put(player.getUUID(), left - 1);
            }
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
        var enemy = ModEffects.ENEMY_OF_THE_VILLAGE.get();
        if (!player.hasEffect(enemy)) {
            // Permanent, no particles (visible=false), shows icon; empty curatives so milk cannot lift it.
            MobEffectInstance inst = new MobEffectInstance(enemy, Integer.MAX_VALUE, 0, false, false, true);
            inst.setCurativeItems(new java.util.ArrayList<>());
            player.addEffect(inst);
        }
    }
}
