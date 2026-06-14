package com.example.villagefightsback;

import net.minecraft.advancements.CriteriaTriggers;

/** Custom advancement triggers. Registered once during common setup (before any datapack loads advancements). */
public final class ModTriggers {
    private ModTriggers() {}

    public static final BrainNodeStageTrigger BRAIN_NODE_STAGE = new BrainNodeStageTrigger();

    public static void register() {
        CriteriaTriggers.register(BRAIN_NODE_STAGE);
    }
}
