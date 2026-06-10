package com.example.villagergunners;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * "Protecting glass": a directional, one-way-for-projectiles glass (see {@link ProtectingGlassEvents}).
 * Looks like light-blue tinted glass on five faces; the {@link #FACING} face (the side a projectile can
 * EXIT) renders white. A projectile travelling roughly along FACING passes through as if it were air; a
 * projectile travelling the other way is stopped by the (otherwise solid) block. As tough as reinforced
 * glass / ancient debris (hardness 30, blast resistance 1200, diamond pickaxe). Used to shell the
 * {@code wall_defender} so it can shoot out while skeletons can't shoot in.
 */
public class ProtectingGlassBlock extends HalfTransparentBlock {
    public static final DirectionProperty FACING = DirectionalBlock.FACING;

    public ProtectingGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // The white "exit" face points the way the placer is looking, so you shoot OUT along your view.
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
