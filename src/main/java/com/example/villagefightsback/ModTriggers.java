package com.example.villagefightsback;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom advancement triggers. In 1.21 the {@code trigger_type} registry is frozen before common setup, so
 * the trigger must be registered through a {@link DeferredRegister} on the mod event bus -- the old
 * {@code CriteriaTriggers.register(...)} at common-setup time now throws "Registry is already frozen".
 */
public final class ModTriggers {
    private ModTriggers() {}

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, TheVillageFightsBack.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, BrainNodeStageTrigger> BRAIN_NODE_STAGE =
            TRIGGERS.register("brain_node_stage", BrainNodeStageTrigger::new);

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }
}
