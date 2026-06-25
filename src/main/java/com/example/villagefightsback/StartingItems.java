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
    }

    /** Once a second, while player_is_hostile is on, keep every player an Enemy of the Village and never a Hero. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!Config.playerIsHostile()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
            // Permanent, no particles (visible=false), shows icon. Milk-proofing now lives on the effect itself
            // (NeoForge replaced per-instance curative items with EffectCure; see EnemyOfVillageEffect).
            MobEffectInstance inst = new MobEffectInstance(enemy, Integer.MAX_VALUE, 0, false, false, true);
            player.addEffect(inst);
        }
    }
}
