package com.example.villagefightsback;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sound-event registration. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TheVillageFightsBack.MODID);

    /** The revolver's gunshot (a local copy of JEG's revolver fire sound). */
    public static final RegistryObject<SoundEvent> REVOLVER_FIRE = SOUNDS.register("revolver_fire",
            () -> new SoundEvent(new ResourceLocation(TheVillageFightsBack.MODID, "revolver_fire")));

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
