package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * General Darius's custom walk-node evaluator. Darius plows STRAIGHT THROUGH generic terrain (he breaks any
 * block softer than ancient debris on contact -- see {@link LimboGear#breakSoftBlocksOnContact}), so for the
 * pathfinder almost every solid block is treated as OPEN / walkable. The ONLY blocks treated as a WALL (so the
 * path routes around them) are:
 *   - "village blocks": the block types a wall plan is built from -- cobblestone, cobblestone stairs, obsidian
 *     + obsidian stairs, reinforced/protecting glass, and the fake-obsidian/fake-cobblestone phase-door gates --
 *     so Darius does not chew through the village's own defensive wall while approaching his target; and
 *   - blocks at least as tough as ancient debris ({@link LimboGear#ANCIENT_DEBRIS_HARDNESS}) or unbreakable
 *     (obsidian, ancient debris, reinforced deepslate, bedrock), which he cannot break anyway.
 *
 * {@code getBlockPathTypeRaw} is {@code static} in vanilla and cannot be overridden, so we override the
 * INSTANCE entry point {@link #getBlockPathType(BlockGetter, int, int, int)} instead (it is what every node
 * cost lookup funnels through). We first ask vanilla, then post-process: a block we cannot break / a village
 * block is forced to {@link BlockPathTypes#BLOCKED}; a generic solid obstacle that vanilla called
 * {@code BLOCKED} (because it is a wall the mob cannot pass) is downgraded to {@code OPEN} so the navigator
 * plots a route straight through it (Darius then clears it physically by contact-break as he walks). Vanilla
 * hazards / specials (lava, fire, fences, doors, water, rails, damaging blocks, ...) are preserved.
 */
public class DariusNodeEvaluator extends WalkNodeEvaluator {

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);

        if (!state.isAir()) {
            // 1) Village blocks: route AROUND them (never treat as walkable/passable).
            if (isVillageBlock(state)) return BlockPathTypes.BLOCKED;
            // 2) Too tough to break (or unbreakable): a real wall to Darius too.
            if (isUnbreakableToDarius(level, pos, state)) return BlockPathTypes.BLOCKED;
        }

        BlockPathTypes vanilla = super.getBlockPathType(level, x, y, z);
        // 3) A generic solid obstacle vanilla calls BLOCKED is breakable terrain -> let Darius path through it.
        if (vanilla == BlockPathTypes.BLOCKED && !state.isAir()
                && !isVillageBlock(state) && !isUnbreakableToDarius(level, pos, state)) {
            return BlockPathTypes.OPEN;
        }
        return vanilla;
    }

    /** A block that is part of (or matches the types in) a village wall plan -- treat as a wall to route around. */
    public static boolean isVillageBlock(BlockState state) {
        // The fake-block phase-doors: never break, never path through (Darius walks around the gate columns).
        if (LimboGear.isPhaseDoor(state)) return true;
        // The concrete block types the WallPlanner places (see WallBlock.BlockType.toState()).
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLESTONE_STAIRS) || state.is(Blocks.OBSIDIAN)) {
            return true;
        }
        if (state.getBlock() instanceof StairBlock && state.is(ModBlocks.OBSIDIAN_STAIRS.get())) return true;
        // Defensive structures made of the same family (reinforced/protecting glass domes, fake bedrock).
        return state.getBlock() instanceof ReinforcedGlassBlock
                || state.getBlock() instanceof ProtectingGlassBlock
                || state.getBlock() instanceof FakeBedrockBlock;
    }

    /** True if Darius cannot break this block (ancient-debris-tough or harder, or unbreakable). */
    private static boolean isUnbreakableToDarius(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.BEDROCK)) return true;
        float h = state.getDestroySpeed(level, pos);
        return h < 0.0F || h >= LimboGear.ANCIENT_DEBRIS_HARDNESS;
    }
}
