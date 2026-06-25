package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reinforced glass: as tough as ancient debris but OPTICALLY CLEAR. A plain full-cube glass block dims light
 * by 1 per block and doesn't let skylight propagate straight down, which makes a thick glass dome (tall glass
 * pillars + a filled shell) noticeably darken the village. So this block dims light by NOTHING (light block 0)
 * and propagates skylight, letting light pass freely like (better than) ordinary glass.
 */
public class ReinforcedGlassBlock extends HalfTransparentBlock {
    public ReinforcedGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0; // no dimming as light passes through (vanilla glass dims by 1)
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true; // skylight passes straight down through the dome
    }
}
