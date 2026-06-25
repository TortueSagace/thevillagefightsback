package com.example.villagefightsback;

import com.google.gson.JsonObject;

import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * Custom advancement trigger: a player is "near a village whose brain node has reached stage N". Fired by the
 * poll in {@link BrainNodeAchievementEvents}. An advancement's criterion specifies the minimum stage, e.g.
 * {@code {"trigger":"thevillagefightsback:brain_node_stage","conditions":{"stage":2}}}.
 */
public class BrainNodeStageTrigger extends SimpleCriterionTrigger<BrainNodeStageTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation(TheVillageFightsBack.MODID, "brain_node_stage");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, EntityPredicate.Composite player, DeserializationContext ctx) {
        return new Instance(player, GsonHelper.getAsInt(json, "stage", 1));
    }

    /** Fire for {@code player} given the stage of the village they are currently near. */
    public void trigger(ServerPlayer player, int currentStage) {
        this.trigger(player, inst -> inst.matches(currentStage));
    }

    public static class Instance extends AbstractCriterionTriggerInstance {
        private final int minStage;

        public Instance(EntityPredicate.Composite player, int minStage) {
            super(ID, player);
            this.minStage = minStage;
        }

        public boolean matches(int currentStage) {
            return currentStage >= minStage;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx) {
            JsonObject o = super.serializeToJson(ctx);
            o.addProperty("stage", minStage);
            return o;
        }
    }
}
