package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The "infested path" (v1.0.3 mob infection): a lime, glowing path-shaped block laid in the ground right below an
 * emerald-converted unit. It spreads fertile/rich blocks like a village dirt path would -- up to a stage-4 brain
 * node radius, but FIVE TIMES SLOWER -- and never proliferates more infested paths itself. The infection engine
 * ({@link com.example.villagefightsback.infection.InfectionManager}) recognises it as a slow proliferation source.
 */
public class InfestedPathBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public InfestedPathBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
