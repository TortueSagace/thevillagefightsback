package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import com.example.villagefightsback.platform.event.PlayerInteractEvent;
import com.example.villagefightsback.platform.event.BlockEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

import com.example.villagefightsback.infection.BlockMatcher;
import com.example.villagefightsback.network.ModNetwork;
import com.example.villagefightsback.network.SpawnHintPacket;

/**
 * Rich blocks stop being free emeralds.
 *
 * <p>They used to hand one over to anybody with a pickaxe, straight from the loot table, which made the whole
 * fertile spread a mining bonus rather than the village's doing: the player got paid for the infection the
 * village was spending its own labour on. A rich block now drops ITSELF, and there are exactly two ways to get
 * the emerald out of it, both of which go through something the village or the Limbo built:
 *
 * <ul>
 *   <li>hand it to a burning Obliteration Furnace, which converts it;</li>
 *   <li>or break it with an Obliterator, which cracks it open on the spot.</li>
 * </ul>
 *
 * <p>The village's own miners are untouched by any of this. {@code MinerGoal} destroys the block without
 * dropping loot and hands the villager an emerald directly, so village income never went through the loot
 * table and does not now.
 */
public final class RichBlockEvents {
    private RichBlockEvents() {}

    /** How long the hint sits on screen. Matches the starter-bell hint it borrows its slot from. */
    private static final int HINT_TICKS = 100;
    /** One mining hint in ten points at the furnace instead of at the villagers. */
    private static final float OBLITERATE_HINT_CHANCE = 0.1F;

    /**
     * Tell a player who just mined a rich block why they did not get an emerald.
     *
     * <p>On the same bottom-of-screen line the starter bell uses rather than in chat: it is the same KIND of
     * message, a one-off nudge about a rule the game has just quietly applied, and chat would bury it under
     * whatever else is going on.
     */
    @SubscribeEvent
    public static void onRichMined(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!BlockMatcher.isRich(event.getState())) return;
        int id = player.getRandom().nextFloat() < OBLITERATE_HINT_CHANCE
                ? SpawnHintPacket.HINT_RICH_OBLITERATE
                : SpawnHintPacket.HINT_RICH_VILLAGERS;
        ModNetwork.sendTo(player, new SpawnHintPacket(HINT_TICKS, id));
    }

    /**
     * An Obliterator cracks a rich block open where it stands: one emerald, no waiting, no furnace.
     *
     * <p>Modelled on {@code LimboPickaxeEvents}, which is the mod's only precedent for taking a left-click off
     * vanilla to control what a block drops. The block is destroyed with loot OFF and the emerald spawned by
     * hand, because {@code destroyBlock}'s flag is all-or-nothing and the loot table now yields the block.
     */
    @SubscribeEvent
    public static void onObliteratorLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!player.getMainHandItem().is(ModItems.OBLITERATOR.get())) return;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!BlockMatcher.isRich(state)) return;

        event.setCanceled(true);   // ours now: vanilla must not also run its own mining on this block
        level.destroyBlock(pos, false, player);
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(Items.EMERALD)));
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.BLOCKS, 1.0F, 1.4F);
        if (!player.getAbilities().instabuild) {
            player.getMainHandItem().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
    }
}
