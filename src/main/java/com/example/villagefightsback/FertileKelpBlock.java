package com.example.villagefightsback;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fertile Kelp: the green, glowing underwater PLANT the infection turns vanilla kelp into as it spreads along a
 * submerged seafloor (see {@code InfectionManager.convertNearbyPlants}). It is the aquatic sibling of
 * {@link FertileGrassBlock}: a walk-through cross plant, emissive so it reads vividly green, always water-filled
 * (so it renders correctly underwater like vanilla kelp), and an "alien" plant that takes root on any non-air
 * block below. Like the fertile family it has no rich variant. Any mob that brushes through it catches the
 * Emerald Virus I (20s) if it is eligible.
 */
public class FertileKelpBlock extends BushBlock {
    public static final MapCodec<FertileKelpBlock> CODEC = simpleCodec(FertileKelpBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0); // full-height kelp column

    public FertileKelpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Always full of water, like vanilla kelp, so it renders submerged instead of leaving an air pocket. */
    @Override
    public FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    /** Alien kelp: roots on any non-air block below (another fertile kelp segment or the seafloor). */
    @Override
    protected boolean mayPlaceOn(BlockState soil, BlockGetter level, BlockPos pos) {
        return !soil.isAir();
    }

    /** Any mob swimming through the alien kelp catches the Emerald Virus I (20s) if it is eligible. */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity le) EmeraldVirus.touch(le);
    }
}
