package com.example.villagefightsback;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import com.example.villagefightsback.platform.registry.DeferredRegister;
import com.example.villagefightsback.platform.ModRegistries;
import com.example.villagefightsback.platform.registry.RegistryObject;

/** Particle registration. */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ModRegistries.PARTICLE_TYPES, TheVillageFightsBack.MODID);

    /** A GREEN recolour of the Warden's sonic-boom ring animation (rendered by the vanilla SonicBoomParticle). */
    public static final RegistryObject<SimpleParticleType> GREEN_SONIC_BOOM =
            PARTICLES.register("green_sonic_boom", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));

    /** JEG's "big explosion" animation -- a scaled-up ExplodeParticle with custom fireball sprites. */
    public static final RegistryObject<SimpleParticleType> GIGACHAD_EXPLOSION =
            PARTICLES.register("gigachad_explosion", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));

    /** A GREEN recolour of {@link #GIGACHAD_EXPLOSION}, fired by the EMERALD gigachad golem's rockets. */
    public static final RegistryObject<SimpleParticleType> GIGACHAD_EXPLOSION_GREEN =
            PARTICLES.register("gigachad_explosion_green", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));

    public static void register() {
        PARTICLES.register();
    }
}
