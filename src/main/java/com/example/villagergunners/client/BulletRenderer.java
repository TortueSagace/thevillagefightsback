package com.example.villagergunners.client;

import com.example.villagergunners.BulletEntity;
import com.example.villagergunners.VillagerGunners;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The bullet itself is invisible (like JEG's, which is a tiny size-0.05 projectile) -- the visible part is the
 * yellow particle tracer spawned in {@link BulletEntity#tick()}. The base EntityRenderer draws no body, so we
 * only need a (never-sampled) texture location.
 */
public class BulletRenderer extends EntityRenderer<BulletEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(VillagerGunners.MODID, "textures/entity/bullet.png");

    public BulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(BulletEntity entity) {
        return TEX;
    }
}
