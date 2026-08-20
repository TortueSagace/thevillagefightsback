package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The village path: what a stage-2 village lays along its wall footprint, in one of five colours.
 *
 * <p>It exists so a world gets colour even with mob infection switched off. The infested path is the emerald
 * faction's, and it only appears where a converted unit died -- so in a village-only world every path the
 * village laid was plain vanilla dirt. This is that path, in the family's colours.
 *
 * <p>It is deliberately IDENTICAL to {@link InfestedPathBlock} to look at: same shape, same glow, same
 * textures. The difference is not visual and not meant to be -- it is that the brain cannot spawn emerald
 * units above one. That falls out of the bookkeeping rather than from any check here: emerald spawn anchors
 * come from InfectionData's tracked infested-path set, and a village path is never added to it.
 *
 * <p>To the infection engine it is a path like any other: {@code BlockMatcher.isPath} accepts it, so a head
 * laid on one grows exactly as a dirt path's would.
 */
public class VillagePathBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.0D, 16.0D);

    public VillagePathBlock(Properties properties) {
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
