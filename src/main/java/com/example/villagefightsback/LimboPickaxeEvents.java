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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lets the Limbo Pickaxe mine the unmineable. Bedrock (-1 hardness) and reinforced deepslate (55 hardness,
 * never drops) can never be broken by the normal mining loop, so we intercept the left-click and shatter them
 * outright -- consuming a point of durability per block. Every other block is left to vanilla mining.
 *
 * <p>Our own bedrock look-alikes (fake and X-ray) are on the same list, since they copy bedrock's -1 hardness,
 * and unlike the vanilla pair they DROP when broken (see {@link #dropsWhenBroken}).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboPickaxeEvents {
    private LimboPickaxeEvents() {}

    private static boolean isUnmineableTarget(BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.REINFORCED_DEEPSLATE)
                || state.is(ModBlocks.FAKE_BEDROCK.get())   // the Limbo's fake bedrock breaks like real bedrock
                || state.is(ModBlocks.X_RAY_BEDROCK.get()); // and so does its see-through sibling
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
        return state.is(ModBlocks.FAKE_BEDROCK.get()) || state.is(ModBlocks.X_RAY_BEDROCK.get());
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.LIMBO_PICKAXE.get())) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!isUnmineableTarget(state)) return;
        if (player.getAbilities().instabuild) return; // creative already breaks instantly

        event.setCanceled(true); // stop vanilla from doing nothing forever
        if (level instanceof ServerLevel sl) {
            sl.destroyBlock(pos, dropsWhenBroken(state), player); // ours come back; vanilla's two do not
            sl.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.7F);
            held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
    }
}
