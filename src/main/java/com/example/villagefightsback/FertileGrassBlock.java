package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fertile Grass: the green, glowing PLANT (a short-grass-like bush, not a full block) that the infection
 * turns ordinary plants into as it spreads. Walk-through (no collision), cutout cross model, emissive so it
 * reads vividly green. It is an "alien" grass, so it takes root on practically any solid block below (incl.
 * our fertile ground), not just dirt. There is no rich variant.
 */
public class FertileGrassBlock extends BushBlock {
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public FertileGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Grows on any non-air block below (alien grass), so it survives on fertile dirt/stone as well as vanilla soil. */
    @Override
    protected boolean mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos) {
        return !soil.isAir();
    }
}
