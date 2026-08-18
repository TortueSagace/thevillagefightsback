package com.example.villagefightsback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Limbo Book: what the Limbo is, how to leave it, and what to arm yourself with while you are there.
 * Handed over automatically the first time a player arrives (see {@link LimboBookEvents}).
 *
 * <p>The Reputation Book's sibling, and read-only for the same reason: there is nothing to write and no state
 * to keep, so this is not a vanilla written book with pages in NBT. Every word lives in the client's code, so a
 * copy a player has carried since their first banishment always says whatever the current version says.
 *
 * <p>1.21 note: main opens the screen straight from here through a {@code DistExecutor} hop. That does not
 * exist on NeoForge, so this sends a zero-field payload instead -- see
 * {@link com.example.villagefightsback.network.OpenLimboBookPacket}. The book still carries no data; the
 * payload only exists to cross the side boundary.
 */
public class LimboBookItem extends Item {
    public LimboBookItem(Properties properties) {
        super(properties);
    }

    /** Always enchanted-looking, like the Reputation Book. It is not a stationery item. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            com.example.villagefightsback.network.ModNetwork.sendTo(sp,
                    com.example.villagefightsback.network.OpenLimboBookPacket.INSTANCE);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }
}
