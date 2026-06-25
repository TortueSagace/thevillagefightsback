package com.example.villagefightsback;

import net.minecraft.advancements.CriteriaTriggers;

/** Custom advancement triggers. Registered once during common setup (before any datapack loads advancements). */
public final class ModTriggers {
    private ModTriggers() {}

    public static final BrainNodeStageTrigger BRAIN_NODE_STAGE = new BrainNodeStageTrigger();

    public static void register() {
        // 1.21: CriteriaTriggers.register takes (String id, CriterionTrigger). The id is the trigger's
        // serialized name (e.g. "thevillagefightsback:brain_node_stage").
        CriteriaTriggers.register(BrainNodeStageTrigger.ID.toString(), BRAIN_NODE_STAGE);
    }
}
