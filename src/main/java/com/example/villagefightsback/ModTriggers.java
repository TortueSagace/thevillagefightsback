package com.example.villagefightsback;

import net.minecraft.advancements.CriteriaTriggers;

/** Custom advancement triggers. Registered once during common setup (before any datapack loads advancements). */
public final class ModTriggers {
    private ModTriggers() {}

    public static final BrainNodeStageTrigger BRAIN_NODE_STAGE = new BrainNodeStageTrigger();
    /** Raised one of the village's dead with the Limbo Scythe. */
    public static final SimpleModTrigger RESURRECTION = new SimpleModTrigger("resurrection");
    /** The Limbo Scythe spent a soul to buy back the player's own death. */
    public static final SimpleModTrigger SCYTHE_SAVE = new SimpleModTrigger("scythe_save");
    /** An Obliterator kill struck a species off the spawn tables. */
    public static final SimpleModTrigger OBLITERATED = new SimpleModTrigger("obliterated");

    public static void register() {
        CriteriaTriggers.register(BRAIN_NODE_STAGE);
        CriteriaTriggers.register(RESURRECTION);
        CriteriaTriggers.register(SCYTHE_SAVE);
        CriteriaTriggers.register(OBLITERATED);
    }
}
