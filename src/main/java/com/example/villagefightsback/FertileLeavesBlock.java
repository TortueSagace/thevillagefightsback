package com.example.villagefightsback;

import net.minecraft.world.level.block.LeavesBlock;

/**
 * Fertile / Rich leaves: a real {@link LeavesBlock} -- so heightmaps (MOTION_BLOCKING_NO_LEAVES), the wall planner
 * and leaf rendering all treat it as foliage -- with normal leaf decay. Decay distance is measured from blocks in
 * the {@code #minecraft:logs} tag, so the fertile/rich logs are added to that tag (see data/minecraft/tags) and
 * fertile leaves attached to their (fertile) tree persist, while detached ones decay like any leaves.
 */
public class FertileLeavesBlock extends LeavesBlock {
    public FertileLeavesBlock(Properties properties) {
        super(properties);
    }

    // --- Flammable, like the vanilla leaves they stand in for -------------------------------------
    //
    // Vanilla wires leaf flammability inside FireBlock.bootStrap, whose setFlammable is private and takes
    // vanilla blocks only, so a modded leaf is simply absent from the fire table and will not burn. The loader
    // exposes the same three numbers as block overrides instead, which is both cleaner (no access transformer)
    // and automatically covers EVERY variant -- green, rich, and all four colour sets -- because they are all
    // this one class.
    //
    // 30 / 60 are exactly vanilla's numbers for oak leaves, so a fertile tree burns at the rate a normal one does.

    @Override
    public boolean isFlammable(net.minecraft.world.level.block.state.BlockState state,
                               net.minecraft.world.level.BlockGetter level,
                               net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return true;
    }

    @Override
    public int getFlammability(net.minecraft.world.level.block.state.BlockState state,
                               net.minecraft.world.level.BlockGetter level,
                               net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return 60;
    }

    @Override
    public int getFireSpreadSpeed(net.minecraft.world.level.block.state.BlockState state,
                                  net.minecraft.world.level.BlockGetter level,
                                  net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return 30;
    }
}
