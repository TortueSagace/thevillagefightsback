package com.example.villagefightsback;

import com.google.gson.JsonObject;

import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A custom advancement trigger that fires on an EVENT and carries no conditions: the thing happened to this
 * player, and that is the whole criterion.
 *
 * <p>One class, many triggers, each identified by the path it is constructed with. The alternative was a file
 * per trigger that differed only in a string, and a second copy of this boilerplate would have been the moment
 * the two started drifting. Anything that does need conditions should still get its own class, the way
 * {@link BrainNodeStageTrigger} carries a minimum stage.
 *
 * <p>Used for the moments no vanilla trigger observes: emptying a grave with the scythe, and the scythe buying
 * back a death. An advancement's criterion is just
 * {@code {"trigger":"thevillagefightsback:<path>"}}.
 */
public class SimpleModTrigger extends SimpleCriterionTrigger<SimpleModTrigger.Instance> {
    private final ResourceLocation id;

    public SimpleModTrigger(String path) {
        this.id = new ResourceLocation(TheVillageFightsBack.MODID, path);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext ctx) {
        return new Instance(id, player);
    }

    /** Fire for {@code player}: it happened to them. */
    public void trigger(ServerPlayer player) {
        this.trigger(player, inst -> true);
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        public Instance(ResourceLocation id, ContextAwarePredicate player) {
            super(id, player);
        }
    }
}
