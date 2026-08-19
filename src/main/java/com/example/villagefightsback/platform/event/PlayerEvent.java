package com.example.villagefightsback.platform.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Base for the player events the mod listens to, matching Forge's nesting so the handlers read the same. */
public class PlayerEvent extends LivingEvent {
    public PlayerEvent(Player player) {
        super(player);
    }

    @Override
    public Player getEntity() {
        return (Player) super.getEntity();
    }

    /** A player finished connecting and is in the world. */
    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }

    /** A player is leaving. Fired while the player is still on the server. */
    public static class PlayerLoggedOutEvent extends PlayerEvent {
        public PlayerLoggedOutEvent(Player player) {
            super(player);
        }
    }

    /** A player finished moving between dimensions. */
    public static class PlayerChangedDimensionEvent extends PlayerEvent {
        private final ResourceKey<Level> from;
        private final ResourceKey<Level> to;

        public PlayerChangedDimensionEvent(Player player, ResourceKey<Level> from, ResourceKey<Level> to) {
            super(player);
            this.from = from;
            this.to = to;
        }

        public ResourceKey<Level> getFrom() {
            return this.from;
        }

        public ResourceKey<Level> getTo() {
            return this.to;
        }
    }

    /**
     * A player entity was replaced -- after a death, or when returning from the End. {@link #getEntity} is the new
     * one and {@link #getOriginal} the old, and anything the mod keeps on the player has to be carried across.
     */
    public static class Clone extends PlayerEvent {
        private final Player original;
        private final boolean wasDeath;

        public Clone(Player player, Player original, boolean wasDeath) {
            super(player);
            this.original = original;
            this.wasDeath = wasDeath;
        }

        public Player getOriginal() {
            return this.original;
        }

        public boolean isWasDeath() {
            return this.wasDeath;
        }
    }

    /** A player started tracking (rendering) another entity. */
    public static class StartTracking extends PlayerEvent {
        private final Entity target;

        public StartTracking(Player player, Entity target) {
            super(player);
            this.target = target;
        }

        public Entity getTarget() {
            return this.target;
        }
    }

    /** A player took the result of a crafting recipe out of the grid. */
    public static class ItemCraftedEvent extends PlayerEvent {
        private final ItemStack crafting;

        public ItemCraftedEvent(Player player, ItemStack crafting) {
            super(player);
            this.crafting = crafting;
        }

        public ItemStack getCrafting() {
            return this.crafting;
        }
    }
}
