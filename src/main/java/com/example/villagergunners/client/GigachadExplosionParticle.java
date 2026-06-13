package com.example.villagergunners.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A faithful copy of JEG's {@code BigExplosion}: the vanilla {@link ExplodeParticle} (an expanding fireball
 * that animates through a sprite sheet) using our custom explosion sprites, scaled up to 7.5 with a 16-tick
 * lifetime and rendered fullbright. Spawned by {@code RocketEntity} so the gigachad rockets blast with JEG's
 * explosion animation instead of the generic TNT poof.
 */
public class GigachadExplosionParticle extends ExplodeParticle {
    protected GigachadExplosionParticle(ClientLevel level, double x, double y, double z,
                                        double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, sprites);
        this.quadSize = 7.5F; // JEG BigExplosion size
        this.lifetime = 16;   // JEG BigExplosion lifetime
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240; // fullbright, like JEG's
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new GigachadExplosionParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
