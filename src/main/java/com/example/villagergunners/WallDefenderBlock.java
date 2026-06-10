package com.example.villagergunners;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Wall defender": a conduit look-alike turret block backed by {@link WallDefenderBlockEntity}. The Village
 * Gravemind drops one inside a 3x3 protecting-glass shell above each gate at level 2. 100 HP, fires a
 * ~10-damage arrow per second at the nearest hostile.
 */
public class WallDefenderBlock extends BaseEntityBlock {
    public WallDefenderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallDefenderBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.WALL_DEFENDER.get(), WallDefenderBlockEntity::serverTick);
    }
}
