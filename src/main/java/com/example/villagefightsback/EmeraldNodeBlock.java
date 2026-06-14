package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Emerald Node: a glowing "raw emerald ore" block (a raw-iron-block / emerald-block hybrid) with
 * ancient-debris toughness and an expanding-ripple animation. Every Emerald Node within a Village Brain
 * Node's income radius raises that village's daily sunset harvest by 10% -- see
 * {@link BrainNodeEvents#handleSunsetIncome}. Placed positions are tracked globally in {@link BrainNodeData}
 * so the harvest bonus is a cheap set lookup rather than a per-sunset block scan.
 */
public class EmeraldNodeBlock extends Block {
    public EmeraldNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide || oldState.is(this)) return;
        BrainNodeData.get(((ServerLevel) level).getServer())
                .addEmeraldNode(GlobalPos.of(level.dimension(), pos.immutable()));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            BrainNodeData.get(((ServerLevel) level).getServer())
                    .removeEmeraldNode(GlobalPos.of(level.dimension(), pos.immutable()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
