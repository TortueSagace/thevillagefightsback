package com.example.villagefightsback;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
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
 *
 * <p>1.21 note: the path is no longer constructed with -- it is REGISTERED with. A trigger's id is now the key
 * it holds in the {@code trigger_type} registry, which is frozen before common setup, so both the {@code path}
 * constructor argument and the {@code getId()} override are gone and {@link ModTriggers} passes the path to
 * {@code TRIGGERS.register(...)} instead. One class still serves many triggers; only the place the name is
 * written down moved. The json-parsing {@code createInstance} is likewise gone, replaced by the {@link Codec}
 * below, and the instance is modelled on vanilla's own condition-free {@code PlayerTrigger.TriggerInstance}.
 */
public class SimpleModTrigger extends SimpleCriterionTrigger<SimpleModTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** Fire for {@code player}: it happened to them. */
    public void trigger(ServerPlayer player) {
        this.trigger(player, inst -> true);
    }

    /**
     * Codec-based criterion instance (1.21). The optional {@code player} predicate is the standard contextual
     * entity check every criterion carries; this trigger adds nothing of its own, which is the whole point of it.
     */
    public record Instance(Optional<ContextAwarePredicate> player)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)
        ).apply(inst, Instance::new));
    }
}
