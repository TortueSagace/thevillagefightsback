package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What a brain node leaves behind when it falls below Nightmare / Mega Brutal: a cold, cracked husk with the
 * glow gone out of it.
 *
 * <p>It is NOT a brain node. It is never registered in {@link BrainNodeData}, which is the single register
 * every "is a brain within reach" test in the mod consults -- infection range, node spawns, settler dispatch,
 * the immunity aura, the lot -- so a dead node counts for none of them without any of those callers needing to
 * learn about it. It is not the brain-node POI either, so villagers do not treat it as a village centre.
 *
 * <p>Two things it deliberately keeps from the live node:
 * <ul>
 *   <li>{@link NodeGuard} still throws a player off it. Mining the husk is still desecration, and a village
 *       that shrugs at a pickaxe in its dead heart reads as furniture.</li>
 *   <li>Nothing else. In particular it does NOT charge reputation the way
 *       {@code VillageBrainNodeBlock.attack} does -- there is no village left here to take offence.</li>
 * </ul>
 *
 * <p>Feeding it a golden apple raises a {@link VillageSpiritEntity}, which flies off to found the village
 * somewhere else; the husk crumbles to Debris behind it. Until then it simply waits -- forever, if the player
 * never comes. Breaking it ends that village for good.
 */
public class DeadBrainNodeBlock extends Block {
    public DeadBrainNodeBlock(Properties properties) {
        super(properties);
    }

    /** Say what it wants the moment it is placed -- an inert block gives no other clue that it can be revived. */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof Player p)) return;
        p.displayClientMessage(Component.literal(
                "§7Feed the dead brain a golden apple for an attempt to bring the Village back to life"), false);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(Items.GOLDEN_APPLE) && !held.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;   // -> the message below
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!player.getAbilities().instabuild) held.shrink(1);
        UnleashedManager.spawnVillageSpirit((ServerLevel) level, pos.immutable(), player);
        return ItemInteractionResult.CONSUME;
    }

    /**
     * Anything else (including an empty hand): say what this is and what it wants. The live node opens its
     * upgrade screen here; a dead one has no economy, no stage and no upgrades to show.
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        player.displayClientMessage(Component.literal(
                "§8Dead brain node. §fFeed it a golden apple to summon a Village Spirit!"), false);
        sl.playSound(null, pos, SoundEvents.SCULK_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 0.6F);
        return InteractionResult.CONSUME;
    }
}
