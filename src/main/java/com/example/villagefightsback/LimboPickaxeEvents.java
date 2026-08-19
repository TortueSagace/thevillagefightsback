package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.example.villagefightsback.platform.event.PlayerInteractEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * Lets the Limbo Pickaxe mine the unmineable. Bedrock (-1 hardness) and reinforced deepslate (55 hardness,
 * never drops) can never be broken by the normal mining loop, so we intercept the left-click and shatter them
 * outright -- consuming a point of durability per block. Every other block is left to vanilla mining.
 *
 * <p>Our own bedrock look-alikes (fake and X-ray) are on the same list, since they copy bedrock's -1 hardness,
 * and unlike the vanilla pair they DROP when broken (see {@link #dropsWhenBroken}).
 */
public final class LimboPickaxeEvents {
    private LimboPickaxeEvents() {}

    private static boolean isUnmineableTarget(BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.REINFORCED_DEEPSLATE)
                || state.is(ModBlocks.FAKE_BEDROCK.get())   // the Limbo's fake bedrock breaks like real bedrock
                || state.is(ModBlocks.X_RAY_BEDROCK.get())   // and so does its see-through sibling
                || state.is(ModBlocks.OBLITERATION_FURNACE.get()); // bedrock-tough by design, so it needs this too
    }

    /**
     * Whether breaking {@code state} with the Limbo Pickaxe should yield the block back.
     *
     * <p>Only OURS. Real bedrock and reinforced deepslate stay unobtainable: the pickaxe exists to let you
     * carve the Limbo and clear a path, not to hand out the two blocks Mojang made unobtainable on purpose.
     * Our own bedrock look-alikes are bought from the Limbo Sage or crafted, so giving them back when you
     * pick them up again is just letting you move a wall you already paid for.
     */
    private static boolean dropsWhenBroken(BlockState state) {
        return state.is(ModBlocks.FAKE_BEDROCK.get()) || state.is(ModBlocks.X_RAY_BEDROCK.get())
                || state.is(ModBlocks.OBLITERATION_FURNACE.get());
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!isUnmineableTarget(state)) return;
        if (player.getAbilities().instabuild) return; // creative already breaks instantly

        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.LIMBO_PICKAXE.get())) {
            // Say why, for the one block on this list a player meets in the ordinary world. Swinging at bedrock
            // and getting nothing is a lesson Minecraft already taught them; swinging at a furnace they watched
            // a village build, with a diamond pickaxe that works on everything else, is not.
            //
            // On the ACTION BAR, not in chat: a held left-click fires this event every tick, and the action bar
            // overwrites itself where twenty lines of chat a second would bury everything else being said.
            if (state.is(ModBlocks.OBLITERATION_FURNACE.get())) {
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .literal("You need a Limbo Pickaxe to mine that!")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return;
        }

        event.setCanceled(true); // stop vanilla from doing nothing forever
        if (level instanceof ServerLevel sl) {
            sl.destroyBlock(pos, dropsWhenBroken(state), player); // ours come back; vanilla's two do not
            sl.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.7F);
            held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
    }
}
