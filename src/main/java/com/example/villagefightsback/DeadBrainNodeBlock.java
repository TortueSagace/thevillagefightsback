package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
    /**
     * True for a husk that was never alive: the dead brain a Survival village is FOUND with.
     *
     * <p>The three ways a husk comes to exist want two different revivals, and this is what tells them apart:
     * <ul>
     *   <li><b>found</b> ({@code true}) -- placed dead by {@code BrainNodeEvents.tryPlaceBrainNode} when a
     *       Survival village is first serviced. There is a whole village standing around it, so the spirit
     *       has nowhere better to be: it lifts off, drops straight back and re-lights this same block.</li>
     *   <li><b>placed</b> ({@code false}) -- a player put the item down. A block item always places the
     *       default state, so this needs no code of its own.</li>
     *   <li><b>died</b> ({@code false}) -- a living node was destroyed and
     *       {@code UnleashedManager.leaveDeadNode} left the husk. Its village is what killed it; there is
     *       nothing here worth staying for.</li>
     * </ul>
     *
     * <p>A block state property rather than an entry in {@link BrainNodeData}: it travels with the block, so
     * it cannot drift out of step with the world the way a position-keyed set does, and it costs no saved
     * data. Both variants use the same model -- this is bookkeeping, not a second appearance.
     */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FOUND =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("found");

    /** How far out to look for a spirit already on its way back. See {@link #use}. */
    private static final double SPIRIT_SEARCH = 16.0;

    public DeadBrainNodeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FOUND, false));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FOUND);
    }

    /**
     * Claim this ground for the village that died here.
     *
     * <p>Registered in {@link BrainNodeData#deadNodes()}, which is kept apart from the live node set so the
     * husk stays invisible to every "is a brain in reach" test -- infection range, node spawns, settler
     * dispatch. The one rule it DOES answer is the claim radius: a village whose brain is lying dead in it
     * already has a brain, so a bell there must not raise a second one.
     */
    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                        BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide || oldState.is(this)) return;
        BrainNodeData.get(((ServerLevel) level).getServer())
                .addDeadNode(net.minecraft.core.GlobalPos.of(level.dimension(), pos.immutable()));
    }

    /** Broken, or spent raising a spirit: the claim goes with it. */
    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                         BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            BrainNodeData.get(((ServerLevel) level).getServer())
                    .removeDeadNode(net.minecraft.core.GlobalPos.of(level.dimension(), pos.immutable()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Say what it wants the moment it is placed -- an inert block gives no other clue that it can be revived. */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof Player p)) return;
        p.displayClientMessage(Component.literal(
                "§7Feed the dead brain a " + UpgradeTribute.revivingItemName()
                + " for an attempt to bring the Village back to life"), false);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        ItemStack held = player.getItemInHand(hand);

        if (UpgradeTribute.isRevivingItem(held)) {
            if (spiritIncoming(sl, pos, state)) {
                player.displayClientMessage(Component.literal(
                        "§bA Village Spirit is already on its way back to this brain."), false);
                return InteractionResult.CONSUME;      // deliberately BEFORE the apple is taken
            }
            if (!player.getAbilities().instabuild) held.shrink(1);
            UnleashedManager.spawnVillageSpirit(sl, pos.immutable(), player, state.getValue(FOUND));
            return InteractionResult.CONSUME;
        }

        // Anything else (including an empty hand): say what this is and what it wants. The live node opens its
        // upgrade screen here; a dead one has no economy, no stage and no upgrades to show.
        player.displayClientMessage(Component.literal(
                "§8Dead brain node. §fFeed it a " + UpgradeTribute.revivingItemName()
                + " to summon a Village Spirit!"), false);
        sl.playSound(null, pos, SoundEvents.SCULK_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 0.6F);
        return InteractionResult.CONSUME;
    }

    /**
     * Is a spirit already flying this husk's revival?
     *
     * <p>Only a FOUND husk can be asked twice. The other kind crumbles to Debris the moment its spirit rises,
     * so the block is simply not there to click again -- but a found one is deliberately left standing, and
     * without this a second apple during those few seconds would raise a second spirit onto the same brain.
     *
     * <p>The query is a {@value #SPIRIT_SEARCH}-block box around one block, run only on a right-click with an
     * apple in hand. That is a handful of chunk sections, not a world sweep.
     */
    private static boolean spiritIncoming(ServerLevel sl, BlockPos pos, BlockState state) {
        if (!state.getValue(FOUND)) return false;
        return !sl.getEntitiesOfClass(VillageSpiritEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(SPIRIT_SEARCH)).isEmpty();
    }
}
