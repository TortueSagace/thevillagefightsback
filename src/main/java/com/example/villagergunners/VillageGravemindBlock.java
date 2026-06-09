package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Village Gravemind block. Its level/emerald state is GLOBAL (see {@link GravemindData}) and shared
 * by every gravemind. Right-click for the village level + emeralds-to-next-level; right-click holding
 * emeralds to donate them and accelerate the evolution. The {@code charges} visual (0-4, reusing the
 * inverted respawn-anchor textures) tracks the global level.
 */
public class VillageGravemindBlock extends Block {
    public static final IntegerProperty CHARGES = IntegerProperty.create("charges", 0, 4);

    public VillageGravemindBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGES, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGES);
    }

    /** Visual charge level (1-4) for a given gravemind level. */
    public static int chargesForLevel(int level) {
        return Math.max(1, Math.min(4, level));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        GravemindData data = GravemindData.get(sl.getServer());
        ItemStack held = player.getItemInHand(hand);

        if (held.is(Items.EMERALD) && !data.isMaxLevel()) {
            int donated = held.getCount();
            int gained = data.addEmeralds(donated);
            if (!player.getAbilities().instabuild) held.shrink(donated);
            // Reflect any level-up on the clicked block immediately; the rest sync via GravemindEvents.
            int target = chargesForLevel(data.getLevel());
            if (state.getValue(CHARGES) != target) {
                sl.setBlock(pos, state.setValue(CHARGES, target), 3);
            }
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (gained > 0) {
                player.displayClientMessage(Component.literal(
                        "§6The Village Gravemind evolves — the village is now level §e" + data.getLevel()
                        + (data.isMaxLevel() ? " §6(max)!" : "§6!")), false);
            } else {
                player.displayClientMessage(info(data), false);
            }
            return InteractionResult.CONSUME;
        }

        player.displayClientMessage(info(data), false);
        return InteractionResult.CONSUME;
    }

    private static Component info(GravemindData data) {
        if (data.isMaxLevel()) {
            return Component.literal("§aVillage Gravemind §7| Level §e" + data.getLevel() + " §7(max)");
        }
        return Component.literal("§aVillage Gravemind §7| Level §e" + data.getLevel()
                + " §7— §a" + data.getEmeralds() + "§7/§a" + data.costForNext()
                + " §7emeralds to level §e" + (data.getLevel() + 1));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide || oldState.is(this)) return;
        ServerLevel sl = (ServerLevel) level;
        GravemindData data = GravemindData.get(sl.getServer());
        data.addBlock(GlobalPos.of(level.dimension(), pos.immutable()));
        int target = chargesForLevel(data.getLevel());
        if (state.getValue(CHARGES) != target) {
            sl.setBlock(pos, state.setValue(CHARGES, target), 3);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            GravemindData data = GravemindData.get(((ServerLevel) level).getServer());
            data.removeBlock(GlobalPos.of(level.dimension(), pos.immutable()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
