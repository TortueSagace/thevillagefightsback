package com.example.villagefightsback;

import net.minecraft.advancements.CriteriaTriggers;

/** Custom advancement triggers. Registered once during common setup (before any datapack loads advancements). */
public final class ModTriggers {
    private ModTriggers() {}

    public static final GravemindStageTrigger GRAVEMIND_STAGE = new GravemindStageTrigger();

    public static void register() {
        CriteriaTriggers.register(GRAVEMIND_STAGE);
    }
}
