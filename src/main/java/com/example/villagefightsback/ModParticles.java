package com.example.villagefightsback;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Particle registration. */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, TheVillageFightsBack.MODID);

    /** A GREEN recolour of the Warden's sonic-boom ring animation (rendered by the vanilla SonicBoomParticle). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GREEN_SONIC_BOOM =
            PARTICLES.register("green_sonic_boom", () -> new SimpleParticleType(true));

    /** JEG's "big explosion" animation -- a scaled-up ExplodeParticle with custom fireball sprites. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GIGACHAD_EXPLOSION =
            PARTICLES.register("gigachad_explosion", () -> new SimpleParticleType(true));

    /** A GREEN recolour of {@link #GIGACHAD_EXPLOSION}, fired by the EMERALD gigachad golem's rockets. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GIGACHAD_EXPLOSION_GREEN =
            PARTICLES.register("gigachad_explosion_green", () -> new SimpleParticleType(true));

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
