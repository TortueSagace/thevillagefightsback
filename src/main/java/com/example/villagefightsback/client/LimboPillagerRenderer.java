package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Pillager;

/** Limbo Pillager: the vanilla pillager renderer (keeps the crossbow layer), re-skinned black-and-white,
 *  scaled 2x to match the baked hitbox, with glowing white eyes/markings in the dark. */
public class LimboPillagerRenderer extends PillagerRenderer {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/limbo_pillager.png");
    private static final ResourceLocation GLOW =
            TheVillageFightsBack.id("textures/entity/limbo_pillager_glow.png");

    public LimboPillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4F;
        this.addLayer(new LimboGlowLayer<>(this, GLOW));
    }

    @Override
    public ResourceLocation getTextureLocation(Pillager entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Pillager entity, PoseStack pose, float partialTick) {
        super.scale(entity, pose, partialTick); // keep the illager's 0.9375 baseline...
        pose.scale(2.0F, 2.0F, 2.0F);           // ...then 2x on top, matching the baked 2x hitbox
    }
}
