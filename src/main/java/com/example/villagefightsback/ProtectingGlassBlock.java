package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    /**
     * One-way for projectiles, done at the COLLISION level so it works for everything that ray-traces against
     * block collision -- vanilla arrows AND JEG bullets (which pass the bullet itself as the clip entity).
     * A non-living entity (a projectile) moving roughly along FACING sees an empty shape and passes through;
     * anything else (mobs, players, and projectiles moving the other way) hits a solid block.
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc) {
            Entity e = ecc.getEntity();
            if (e != null && !(e instanceof LivingEntity)) { // a projectile (arrow, JEG bullet, ...)
                Direction f = state.getValue(FACING);
                Vec3 v = e.getDeltaMovement();
                double dot = v.x * f.getStepX() + v.y * f.getStepY() + v.z * f.getStepZ();
                if (dot > 0) return Shapes.empty(); // outward -> pass through
            }
        }
        return Shapes.block();
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
