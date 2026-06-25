package com.example.villagefightsback;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

/**
 * Custom advancement trigger: a player is "near a village whose brain node has reached stage N". Fired by the
 * poll in {@link BrainNodeAchievementEvents}. An advancement's criterion specifies the minimum stage, e.g.
 * {@code {"trigger":"thevillagefightsback:brain_node_stage","conditions":{"stage":2}}}.
 */
public class BrainNodeStageTrigger extends SimpleCriterionTrigger<BrainNodeStageTrigger.Instance> {
    public static final ResourceLocation ID = TheVillageFightsBack.id("brain_node_stage");

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** Fire for {@code player} given the stage of the village they are currently near. */
    public void trigger(ServerPlayer player, int currentStage) {
        this.trigger(player, inst -> inst.matches(currentStage));
    }

    /**
     * Codec-based criterion instance (1.21). The optional {@code player} predicate is the standard contextual
     * entity check; {@code stage} is the minimum brain-node stage the village must have reached.
     */
    public record Instance(Optional<ContextAwarePredicate> player, int minStage)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("stage", 1).forGetter(Instance::minStage)
        ).apply(inst, Instance::new));

        public boolean matches(int currentStage) {
            return currentStage >= minStage;
        }
    }
}
