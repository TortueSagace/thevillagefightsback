package com.example.villagefightsback.platform.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** Particle providers, in Forge's shape, over Fabric's particle factory registry. */
@Environment(EnvType.CLIENT)
public final class RegisterParticleProvidersEvent {
    /**
     * A provider built from a sprite set, which is what every one of this mod's particles uses (they all reuse a
     * vanilla particle renderer with their own sprites).
     */
    public <T extends ParticleOptions> void registerSpriteSet(
            ParticleType<T> type, ParticleFactoryRegistry.PendingParticleFactory<T> factory) {
        ParticleFactoryRegistry.getInstance().register(type, factory);
    }
}
