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

    /** Mining a node costs reputation -- village property, priced like the village's metal rather than like
     *  its heart. The brain node itself is a far heavier charge; see {@code VillageBrainNodeBlock.attack}. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        super.attack(state, level, pos, player);
        if (level.isClientSide) return;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            ReputationEvents.onMinedVillageNode(sp);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide || oldState.is(this)) return;
        ServerLevel sl = (ServerLevel) level;
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        BrainNodeData.get(sl.getServer()).addNode(gp);
        // Spawn on genuine creation only -- NOT on a piston relocation (moved), which would be a free spawn.
        // The payload spawn runs INSIDE the rising-building animation (this onPlace fires as HouseBuildManager
        // sets the node block during a TNT build), and it is standalone: triggerNodeSpawn spawns at the nearest
        // brain's stage, or STAGE 1 when no brain is in range -- so a player-placed node TNT always behaves as
        // if a stage-1 node were nearby. Guarded so a payload-spawn hiccup can NEVER abort the building animation
        // (the structure must always finish, with or without a brain).
        if (!moved) {
            try {
                BrainNodeEvents.triggerNodeSpawn(sl, pos.immutable());
            } catch (Throwable t) {
                TheVillageFightsBack.LOGGER.error("[The Village Fights Back] node payload spawn failed at {}", pos, t);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
            BrainNodeData.get(((ServerLevel) level).getServer()).removeNode(gp);
            BrainNodeEvents.forgetNode(gp); // drop its once-per-day spawn stamp so the map stays bounded
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
