package com.example.villagefightsback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Hands over the {@link LimboBookItem} the first time a player arrives in the Limbo.
 *
 * <p>On arrival rather than at spawn, because the book is useless until you are here and its first page is the
 * way out -- it should be in the hand of somebody who is currently lost, not buried in a chest from day one.
 *
 * <p>Remembered in {@code Player.PERSISTED_NBT_TAG}, the one bag of player data that survives death, a
 * dimension change and a relog, so "the first time" means once ever rather than once per visit.
 *
 * <p><b>Only when there is a free slot.</b> Unlike the starter bell this never drops on the floor: an item
 * dropped in the Limbo lands in a bedrock cell or falls into the void, and a book explaining how to escape is
 * the worst possible thing to lose that way. If the inventory is full the flag is deliberately NOT set, so the
 * next visit tries again.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LimboBookEvents {
    private LimboBookEvents() {}

    private static final String NBT_GOT_LIMBO_BOOK = "vgun_got_limbo_book";

    @SubscribeEvent
    public static void onEnterLimbo(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!LimboDimension.LIMBO.equals(event.getTo())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(NBT_GOT_LIMBO_BOOK)) return;
        if (player.getInventory().getFreeSlot() < 0) return;    // no room -- try again next time they arrive

        persisted.putBoolean(NBT_GOT_LIMBO_BOOK, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.getInventory().add(new ItemStack(ModItems.LIMBO_BOOK.get()));
        TheVillageFightsBack.LOGGER.info("[The Village Fights Back] Limbo Book given to {} on first arrival.",
                player.getGameProfile().getName());
    }
}
