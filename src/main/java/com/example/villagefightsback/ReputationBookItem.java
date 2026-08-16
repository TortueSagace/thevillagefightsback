package com.example.villagefightsback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Reputation Book: the rules of standing, in the player's hands from the first morning.
 *
 * <p>Read-only. There is nothing to write and no state to keep, so this is not a vanilla written book with an
 * author and pages in NBT -- it is an item that opens a screen, and every word of it lives in the client's
 * code. That also means an existing copy is never out of date: change the text, ship it, and the book a player
 * has been carrying since day one says the new thing.
 *
 * <p>WHAT IT SAYS DEPENDS ON THE DIFFICULTY, which the client does not know: the difficulty lives in a COMMON
 * config and Forge does not sync those. So the difficulty travels in the packet and the pages are chosen on
 * arrival, the same shape {@code SpawnHintPacket} uses for its lines.
 */
public class ReputationBookItem extends Item {
    public ReputationBookItem(Properties properties) {
        super(properties);
    }

    /** Always enchanted-looking. It is the village's own book, not a stationery item. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            com.example.villagefightsback.network.ModNetwork.sendTo(sp,
                    new com.example.villagefightsback.network.OpenReputationBookPacket(
                            Difficulty.current().id(),
                            ReputationData.get(sp.serverLevel().getServer()).get(sp.getUUID())));
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }
}
