package com.example.villagefightsback;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Obliteration Furnace: a blast furnace that burns emeralds and, while lit, keeps a list of creatures that
 * may not spawn anywhere in any dimension.
 *
 * <p>Fuel goes in by hand -- right-click with emeralds or emerald blocks. There is no fuel SLOT, and that is
 * deliberate: a slot would let a hopper feed the furnace forever, and a permanent global spawn ban with no
 * ongoing cost is a very different feature from the one asked for. Feeding it yourself keeps the ban
 * something you maintain.
 *
 * <p>Right-click empty-handed to read it: how long it has left, and everything it currently holds erased.
 *
 * <p>Its state lives in {@link ObliterationData} rather than in the block entity, so the fuel burns down and
 * the ban holds while nobody is anywhere near it. The block entity's only job is to keep {@link #LIT} in step
 * with that record, so the furnace looks lit exactly when it is.
 */
public class ObliterationFurnaceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** One emerald buys a minute; a block buys ten, so a block is worth more than its nine emeralds. */
    public static final int TICKS_PER_EMERALD = 20 * 60;
    public static final int TICKS_PER_BLOCK = 20 * 60 * 10;

    public ObliterationFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;   // BaseEntityBlock defaults to INVISIBLE, which would hide the furnace
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObliterationFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlocks.OBLITERATION_FURNACE_BE.get(),
                ObliterationFurnaceBlockEntity::serverTick);
    }

    /** Fuel value of one item of {@code stack}, or 0 if it is not fuel. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(Items.EMERALD)) return TICKS_PER_EMERALD;
        if (stack.is(Items.EMERALD_BLOCK)) return TICKS_PER_BLOCK;
        return 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        GlobalPos key = GlobalPos.of(sl.dimension(), pos.immutable());
        ObliterationData data = ObliterationData.get(sl.getServer());
        ItemStack held = player.getItemInHand(hand);
        int per = fuelValue(held);

        if (per > 0) {
            // Feed ONE item per click. Emptying a stack in a single click makes it far too easy to sink an
            // inventory of emerald blocks into a ban you cannot then undo except by waiting it out.
            if (!player.getAbilities().instabuild) held.shrink(1);
            int total = data.addFuel(key, per);
            ObliterationFurnaceBlockEntity.syncLit(sl, pos, true);
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .literal("The furnace drinks it in. " + minutes(total) + " remaining.")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            sl.playSound(null, pos, net.minecraft.sounds.SoundEvents.BLASTFURNACE_FIRE_CRACKLE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);
            return InteractionResult.CONSUME;
        }

        // A rich block, fed in, comes back out as the emerald inside it. This is one of the only two ways the
        // player can get an emerald out of a rich block since they stopped dropping one when mined, and it
        // sits BETWEEN the fuel branch and the screen: anything the furnace does not eat opens the screen, so
        // a conversion added after that fall-through would never be reached.
        //
        // The furnace has to be BURNING. A cold one is a decorative block, and the whole economy of the thing
        // is that it costs fuel to do anything at all.
        if (com.example.villagefightsback.infection.BlockMatcher.isRich(
                net.minecraft.world.level.block.Block.byItem(held.getItem()).defaultBlockState())) {
            if (!data.isBurning(key)) {
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .literal("The furnace is cold. Feed it an emerald first.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                return InteractionResult.CONSUME;
            }
            int converted = player.getAbilities().instabuild ? 1 : held.getCount();
            if (!player.getAbilities().instabuild) held.shrink(converted);
            ItemStack emeralds = new ItemStack(net.minecraft.world.item.Items.EMERALD, converted);
            if (!player.getInventory().add(emeralds)) player.drop(emeralds, false);
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .literal("The furnace renders it down: " + converted + " emerald"
                            + (converted == 1 ? "" : "s") + ".")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            sl.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.2F);
            return InteractionResult.CONSUME;
        }

        ObliterationFurnaceScreenPacket.open(player, key, data);
        return InteractionResult.CONSUME;
    }

    /** "12 minutes" / "less than a minute", for the fuel message. */
    public static String minutes(int ticks) {
        int m = ticks / (20 * 60);
        if (m <= 0) return "Less than a minute";
        return m + (m == 1 ? " minute" : " minutes");
    }

    /** A furnace that is broken stops banning anything: drop its record with it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            ObliterationData.get(sl.getServer()).remove(GlobalPos.of(sl.dimension(), pos.immutable()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                  net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
