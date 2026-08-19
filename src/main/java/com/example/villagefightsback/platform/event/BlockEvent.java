package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Base for the two block events the mod listens to, matching Forge's nesting. */
public class BlockEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    public BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public LevelAccessor getLevel() {
        return this.level;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public BlockState getState() {
        return this.state;
    }

    /** A player is about to break a block. Cancelling leaves it standing. */
    public static class BreakEvent extends BlockEvent {
        private final Player player;

        public BreakEvent(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() {
            return this.player;
        }
    }

    /** An entity placed a block. {@link #getPlacedBlock} is the new state. */
    public static class EntityPlaceEvent extends BlockEvent {
        private final Entity entity;
        private final BlockState placedBlock;
        private final BlockState placedAgainst;

        public EntityPlaceEvent(LevelAccessor level, BlockPos pos, BlockState placedBlock,
                                BlockState placedAgainst, Entity entity) {
            super(level, pos, placedBlock);
            this.entity = entity;
            this.placedBlock = placedBlock;
            this.placedAgainst = placedAgainst;
        }

        public Entity getEntity() {
            return this.entity;
        }

        public BlockState getPlacedBlock() {
            return this.placedBlock;
        }

        public BlockState getPlacedAgainst() {
            return this.placedAgainst;
        }
    }
}
