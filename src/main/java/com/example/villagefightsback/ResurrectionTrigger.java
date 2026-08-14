package com.example.villagefightsback;

import com.google.gson.JsonObject;

import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Custom advancement trigger: a player has raised one of the village's dead with the Limbo Scythe.
 *
 * <p>Needs to be custom because no vanilla trigger comes close. {@code inventory_changed} fires on HOLDING the
 * scythe, which is what earns the previous advancement in the chain, and nothing vanilla observes a grave being
 * emptied. Fired from {@link LimboScytheItem} on the success branch only, so failing for want of a soul earns
 * nothing.
 *
 * <p>No conditions: raising anyone at all is the achievement. An advancement's criterion is therefore just
 * {@code {"trigger":"thevillagefightsback:resurrection"}}.
 */
public class ResurrectionTrigger extends SimpleCriterionTrigger<ResurrectionTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation(TheVillageFightsBack.MODID, "resurrection");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx) {
        return new Instance(player);
    }

    /** Fire for {@code player}, who has just brought someone back. */
    public void trigger(ServerPlayer player) {
        this.trigger(player, inst -> true);
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        public Instance(ContextAwarePredicate player) {
            super(ID, player);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx) {
            return super.serializeToJson(ctx);
        }
    }
}
