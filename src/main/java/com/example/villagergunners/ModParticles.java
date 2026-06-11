package com.example.villagergunners;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particle registration. */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, VillagerGunners.MODID);

    /** A GREEN recolour of the Warden's sonic-boom ring animation (rendered by the vanilla SonicBoomParticle). */
    public static final RegistryObject<SimpleParticleType> GREEN_SONIC_BOOM =
            PARTICLES.register("green_sonic_boom", () -> new SimpleParticleType(true));

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
