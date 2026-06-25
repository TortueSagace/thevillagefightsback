package com.example.villagefightsback;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The Fertile Chorus Flower: grows EXACTLY like a vanilla chorus flower, but spreads OUR fertile chorus
 * (plant + flower) instead of vanilla chorus. This is a near-verbatim copy of vanilla
 * {@link net.minecraft.world.level.block.ChorusFlowerBlock} with {@code Blocks.CHORUS_FLOWER} /
 * {@code Blocks.CHORUS_PLANT} swapped for {@link ModBlocks#FERTILE_CHORUS_FLOWER} /
 * {@link ModBlocks#FERTILE_CHORUS_PLANT}. The vanilla growth/age logic is preserved.
 *
 * <p>It extends vanilla {@link ChorusFlowerBlock} so renderers / item logic that test {@code instanceof
 * ChorusFlowerBlock} still work; we override the growth methods to reference our blocks.</p>
 */
public class FertileChorusFlowerBlock extends ChorusFlowerBlock {
    public static final int DEAD_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;

    /** The chorus PLANT this flower spreads (fertile flower -> fertile plant, rich flower -> rich plant). */
    private final java.util.function.Supplier<? extends Block> plantSupplier;

    /** Constructed with the chorus PLANT it spreads, and properties copied from CHORUS_FLOWER. */
    public FertileChorusFlowerBlock(BlockBehaviour.Properties properties,
            java.util.function.Supplier<? extends Block> plant) {
        // The super needs SOME ChorusPlantBlock; we never use the vanilla one (every method that touches
        // it is overridden below to use our plant), so pass vanilla CHORUS_PLANT to satisfy it.
        super((ChorusPlantBlock) Blocks.CHORUS_PLANT, properties);
        this.plantSupplier = plant;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    /** Our chorus PLANT block (the body the flower leaves behind / grows on). */
    private ChorusPlantBlock plant() {
        return (ChorusPlantBlock) plantSupplier.get();
    }

    // Resolved lazily (after registration); our "fertile_end_stone" has no static accessor in ModBlocks.
    @Nullable
    private static Block fertileEndStone;

    private static Block fertileEndStone() {
        if (fertileEndStone == null) {
            fertileEndStone = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    TheVillageFightsBack.id("fertile_end_stone"));
        }
        return fertileEndStone;
    }

    /**
     * Ground the fertile chorus may sprout / continue growing from: vanilla end stone, our fertile end stone,
     * or -- being robust per the spec -- any solid (motion-blocking, non-air) block below.
     */
    private static boolean isGround(BlockState below) {
        if (below.is(Blocks.END_STONE)) return true;
        Block fes = fertileEndStone();
        if (fes != null && below.is(fes)) return true;
        return !below.isAir() && below.blocksMotion();
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < DEAD_AGE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        if (level.isEmptyBlock(above) && above.getY() < level.getMaxBuildHeight()) {
            int age = state.getValue(AGE);
            if (age < DEAD_AGE) {
                boolean grow = false;
                boolean overEndStone = false;
                BlockState below = level.getBlockState(pos.below());
                Block plant = plant();
                if (isGround(below)) {
                    grow = true;
                } else if (below.is(plant)) {
                    int height = 1;

                    for (int k = 0; k < 4; ++k) {
                        BlockState lower = level.getBlockState(pos.below(height + 1));
                        if (!lower.is(plant)) {
                            if (isGround(lower)) {
                                overEndStone = true;
                            }
                            break;
                        }

                        ++height;
                    }

                    if (height < 2 || height <= random.nextInt(overEndStone ? 5 : 4)) {
                        grow = true;
                    }
                } else if (below.isAir()) {
                    grow = true;
                }

                if (grow && allNeighborsEmpty(level, above, null) && level.isEmptyBlock(pos.above(2))) {
                    level.setBlock(pos, ((FertileChorusPlantBlock) plant()).getFertileStateForPlacement(level, pos), 2);
                    placeGrownFlower(level, above, age);
                } else if (age < 4) {
                    int spread = random.nextInt(4);
                    if (overEndStone) {
                        ++spread;
                    }

                    boolean spawned = false;

                    for (int i1 = 0; i1 < spread; ++i1) {
                        Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        BlockPos side = pos.relative(dir);
                        if (level.isEmptyBlock(side) && level.isEmptyBlock(side.below())
                                && allNeighborsEmpty(level, side, dir.getOpposite())) {
                            placeGrownFlower(level, side, age + 1);
                            spawned = true;
                        }
                    }

                    if (spawned) {
                        level.setBlock(pos, ((FertileChorusPlantBlock) plant()).getFertileStateForPlacement(level, pos), 2);
                    } else {
                        placeDeadFlower(level, pos);
                    }
                } else {
                    placeDeadFlower(level, pos);
                }
            }
        }
    }

    private void placeGrownFlower(Level level, BlockPos pos, int age) {
        level.setBlock(pos, this.defaultBlockState().setValue(AGE, Integer.valueOf(age)), 2);
        level.levelEvent(1033, pos, 0);
    }

    private void placeDeadFlower(Level level, BlockPos pos) {
        level.setBlock(pos, this.defaultBlockState().setValue(AGE, Integer.valueOf(DEAD_AGE)), 2);
        level.levelEvent(1034, pos, 0);
    }

    private static boolean allNeighborsEmpty(LevelReader level, BlockPos pos, @Nullable Direction except) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir != except && !level.isEmptyBlock(pos.relative(dir))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction != Direction.UP && !state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 1);
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        Block plant = plant();
        // Allow growing on our fertile chorus plant, end stone, OR our fertile end stone (mirrors vanilla,
        // plus our fertile ground). If it's on any of those, it's fine immediately.
        if (below.is(plant) || isGround(below)) {
            return true;
        } else if (!below.isAir()) {
            return false;
        } else {
            boolean flag = false;

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState side = level.getBlockState(pos.relative(direction));
                if (side.is(plant)) {
                    if (flag) {
                        return false;
                    }

                    flag = true;
                } else if (!side.isAir()) {
                    return false;
                }
            }

            return flag;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
