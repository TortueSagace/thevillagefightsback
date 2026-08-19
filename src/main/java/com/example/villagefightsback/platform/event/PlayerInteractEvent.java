package com.example.villagefightsback.platform.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The three player interactions the mod hooks, matching Forge's nesting. */
public class PlayerInteractEvent extends PlayerEvent {
    private final InteractionHand hand;
    private final BlockPos pos;
    private final Direction face;
    private InteractionResult cancellationResult = InteractionResult.PASS;

    public PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos, Direction face) {
        super(player);
        this.hand = hand;
        this.pos = pos;
        this.face = face;
    }

    public InteractionHand getHand() {
        return this.hand;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    /** The side of the block that was clicked, or null when there is none. */
    public Direction getFace() {
        return this.face;
    }

    public Level getLevel() {
        return getEntity().level();
    }

    public ItemStack getItemStack() {
        return getEntity().getItemInHand(this.hand);
    }

    /** What vanilla should report back for this interaction once it has been cancelled. */
    public void setCancellationResult(InteractionResult result) {
        this.cancellationResult = result;
    }

    public InteractionResult getCancellationResult() {
        return this.cancellationResult;
    }

    /** A left click on a block: the start of breaking it, or a swing at it. */
    public static class LeftClickBlock extends PlayerInteractEvent {
        public LeftClickBlock(Player player, BlockPos pos, Direction face) {
            super(player, InteractionHand.MAIN_HAND, pos, face);
        }
    }

    /** A right click on a block. */
    public static class RightClickBlock extends PlayerInteractEvent {
        public RightClickBlock(Player player, InteractionHand hand, BlockPos pos, Direction face) {
            super(player, hand, pos, face);
        }
    }

    /** A right click on an entity, with the exact hit position known. */
    public static class EntityInteractSpecific extends PlayerInteractEvent {
        private final Entity target;

        public EntityInteractSpecific(Player player, InteractionHand hand, Entity target) {
            super(player, hand, target.blockPosition(), null);
            this.target = target;
        }

        public Entity getTarget() {
            return this.target;
        }
    }
}
