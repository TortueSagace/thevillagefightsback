package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base for the three "node" blocks (Golem Factory / Barracks / Lure Tower). All share the Emerald-Node
 * heritage: glowing, ancient-debris-tough ore blocks that, within a Village Brain Node's income radius, add
 * +10% to that village's daily sunset emerald harvest (their positions are tracked in
 * {@link BrainNodeData#nodes()}). Each subclass ALSO spawns its own reinforcement -- once every sunset, and
 * once the instant the block is created -- via {@link #spawnPayload}, but only when a brain node is in range
 * (see {@link BrainNodeEvents#triggerNodeSpawn}).
 */
public abstract class AbstractNodeBlock extends Block {
    protected AbstractNodeBlock(Properties properties) { super(properties); }

    /** Spawn this node's reinforcement at/near {@code node}, given the in-range brain node's {@code stage} (1..4). */
    public abstract void spawnPayload(ServerLevel level, BlockPos node, int stage);

    /** Mining a node turns the village on the miner -- same anti-grief response as the brain node. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        super.attack(state, level, pos, player);
        if (level.isClientSide) return;
        VillageEvents.markVillageEnemy((ServerLevel) level, pos, player);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide || oldState.is(this)) return;
        ServerLevel sl = (ServerLevel) level;
        BrainNodeData.get(sl.getServer()).addNode(GlobalPos.of(level.dimension(), pos.immutable()));
        BrainNodeEvents.triggerNodeSpawn(sl, pos.immutable()); // spawn on creation, if a brain node is in range
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            BrainNodeData.get(((ServerLevel) level).getServer())
                    .removeNode(GlobalPos.of(level.dimension(), pos.immutable()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
