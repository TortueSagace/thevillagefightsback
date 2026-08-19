package com.example.villagefightsback;

import com.example.villagefightsback.infection.BlockMatcher;
import com.example.villagefightsback.infection.InfectionData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fertile Grass: the green, glowing PLANT (a short-grass-like bush, not a full block) that the infection
 * turns ordinary plants into as it spreads. Walk-through (no collision), cutout cross model, emissive so it
 * reads vividly green. It is an "alien" grass, so it takes root on practically any solid block below (incl.
 * our fertile ground), not just dirt. There is no rich variant. As living alien grass it slowly FERTILIZES
 * the soil under it: on a random tick it converts the block below into its Fertile variant (see {@link #randomTick}).
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

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true; // so it can fertilize the soil beneath it over time
    }

    /** Any mob wading through the alien grass catches the Emerald Virus I (20s) if it is eligible. */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity le) EmeraldVirus.touch(le);
    }

    /** Convert the block directly below into its Fertile variant (the same rules the spreading infection uses),
     *  so alien grass standing on ordinary ground slowly turns that ground fertile. Counts toward the infection
     *  footprint so the brain-node UI tally stays accurate; no-ops once the soil is already infection/uneligible. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos below = pos.below();
        // Refuse inside a protected structure volume.
        if (com.example.villagefightsback.infection.StructureZones.protectedAt(level, below)) return;
        // This used to be the ONE route with no brain-node range gate at all, and so the way the infection
        // reached ground the village had no business touching. It now honours the same rule as everything
        // else, off a snapshot the infection pass refreshes every 5 ticks (a random tick is far too frequent
        // to walk the node list itself).
        if (com.example.villagefightsback.Config.infestedNeedsBrainNode()
                && !com.example.villagefightsback.infection.InfectionManager.withinBrainReach(level, below)) return;
        BlockState soil = level.getBlockState(below);
        if (!BlockMatcher.isCandidate(level, below, soil)) return;
        BlockState converted = BlockMatcher.convertedState(soil, false); // fertile (not rich)
        if (converted == null || converted.is(soil.getBlock())) return;
        level.setBlock(below, converted, 3);
        InfectionData data = InfectionData.get(level);
        data.incConverted();
        // AND join the frontier, which this never did. A block converted here but left out of the frontier is
        // an orphan: the engine never walks it, so it never spreads -- the whole point of fertilizing the soil
        // under a plant -- and when something breaks it, the branch in InfectionManager that notices a lost
        // cell (the only caller of decConverted) never runs either, so the converted tally drifts up forever
        // and inflates both the brain node's count and the footprint cap.
        data.addFrontier(below.asLong());
    }
}
