package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Fertile Chorus Plant: the green-tinted body block our {@link FertileChorusFlowerBlock} grows. Vanilla
 * {@link ChorusPlantBlock} only connects to {@code Blocks.CHORUS_PLANT}/{@code CHORUS_FLOWER}, so we override
 * the connection logic to instead recognise OUR fertile chorus plant/flower (and end stone / fertile ground
 * below). Everything else (shape, pathfinding, age-less PipeBlock state) is inherited from vanilla.
 */
public class FertileChorusPlantBlock extends ChorusPlantBlock {
    // Resolved lazily after registration -- our fertile_end_stone has no static accessor in ModBlocks.
    private static Block fertileEndStone;

    /** The chorus FLOWER this plant tops out with (fertile plant -> fertile flower, rich -> rich flower). */
    private final java.util.function.Supplier<? extends Block> flowerSupplier;

    public FertileChorusPlantBlock(BlockBehaviour.Properties properties,
            java.util.function.Supplier<? extends Block> flower) {
        super(properties);
        this.flowerSupplier = flower;
    }

    private static Block fertileEndStone() {
        if (fertileEndStone == null) {
            fertileEndStone = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(TheVillageFightsBack.MODID, "fertile_end_stone"));
        }
        return fertileEndStone;
    }

    /**
     * Ground a chorus plant may STAND on: vanilla end stone, our fertile end stone, or -- so it can establish
     * on the fertile land the flower grows on -- any solid (motion-blocking, non-air) block. MUST match
     * {@link FertileChorusFlowerBlock}'s ground check, else the plant the flower places fails canSurvive and
     * the chorus can never grow off end stone (the "never grows" bug).
     */
    private static boolean isGround(BlockState s) {
        if (s.is(Blocks.END_STONE)) return true;
        Block fes = fertileEndStone();
        if (fes != null && s.is(fes)) return true;
        return !s.isAir() && s.getMaterial().blocksMotion();
    }

    /** Our chorus FLOWER (the top this plant connects up to). */
    private Block fertileFlower() {
        return flowerSupplier.get();
    }

    /** A neighbour the plant connects to horizontally / upward: our fertile chorus plant or flower. */
    private boolean connectsTo(BlockState neighbor) {
        return neighbor.is(this) || neighbor.is(fertileFlower());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.getStateForPlacement(context.getLevel(), context.getClickedPos());
    }

    /**
     * Compute the 6 directional connection booleans (the PipeBlock NORTH..DOWN properties) based on our
     * fertile chorus plant/flower neighbours; DOWN additionally connects to end stone / fertile end stone.
     * Mirrors vanilla {@link ChorusPlantBlock#getStateForPlacement(BlockGetter, BlockPos)}.
     */
    @Override
    public BlockState getStateForPlacement(BlockGetter level, BlockPos pos) {
        BlockState down = level.getBlockState(pos.below());
        BlockState up = level.getBlockState(pos.above());
        BlockState north = level.getBlockState(pos.north());
        BlockState east = level.getBlockState(pos.east());
        BlockState south = level.getBlockState(pos.south());
        BlockState west = level.getBlockState(pos.west());
        return this.defaultBlockState()
                .setValue(DOWN, Boolean.valueOf(connectsTo(down) || isGround(down)))
                .setValue(UP, Boolean.valueOf(connectsTo(up)))
                .setValue(NORTH, Boolean.valueOf(connectsTo(north)))
                .setValue(EAST, Boolean.valueOf(connectsTo(east)))
                .setValue(SOUTH, Boolean.valueOf(connectsTo(south)))
                .setValue(WEST, Boolean.valueOf(connectsTo(west)));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 1);
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        } else {
            boolean connect = connectsTo(neighborState)
                    || (direction == Direction.DOWN && isGround(neighborState));
            return state.setValue(PROPERTY_BY_DIRECTION.get(direction), Boolean.valueOf(connect));
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    /**
     * A fertile chorus plant survives if it is connected to a vertical column standing on end-stone-like
     * ground, or rests directly on it. Mirrors vanilla {@link ChorusPlantBlock#canSurvive} but with our
     * plant/end-stone checks.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        boolean cappedBoth = !level.getBlockState(pos.above()).isAir() && !below.isAir();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = pos.relative(direction);
            BlockState side = level.getBlockState(sidePos);
            if (side.is(this)) {
                if (cappedBoth) {
                    return false;
                }

                BlockState sideBelow = level.getBlockState(sidePos.below());
                if (sideBelow.is(this) || isGround(sideBelow)) {
                    return true;
                }
            }
        }

        return below.is(this) || isGround(below);
    }
}
