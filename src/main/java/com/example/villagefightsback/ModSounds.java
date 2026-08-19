package com.example.villagefightsback;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import com.example.villagefightsback.platform.registry.DeferredRegister;
import com.example.villagefightsback.platform.ModRegistries;
import com.example.villagefightsback.platform.registry.RegistryObject;

/** Sound-event registration. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ModRegistries.SOUND_EVENTS, TheVillageFightsBack.MODID);

    /** The revolver's gunshot (a local copy of JEG's revolver fire sound). */
    public static final RegistryObject<SoundEvent> REVOLVER_FIRE = SOUNDS.register("revolver_fire",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TheVillageFightsBack.MODID, "revolver_fire")));

    public static void register() {
        SOUNDS.register();
    }
}
