package com.example.villagefightsback;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Sound-event registration. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, TheVillageFightsBack.MODID);

    /** The revolver's gunshot (a local copy of JEG's revolver fire sound). */
    public static final DeferredHolder<SoundEvent, SoundEvent> REVOLVER_FIRE = SOUNDS.register("revolver_fire",
            () -> SoundEvent.createVariableRangeEvent(TheVillageFightsBack.id("revolver_fire")));

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
