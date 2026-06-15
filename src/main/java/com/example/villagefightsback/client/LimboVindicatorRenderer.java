package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VindicatorRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Vindicator;

/** Limbo Vindicator: the vanilla vindicator renderer (keeps the axe layer), re-skinned black-and-white,
 *  scaled 2x to match the baked hitbox, with glowing white eyes/markings in the dark. */
public class LimboVindicatorRenderer extends VindicatorRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_vindicator.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_vindicator_glow.png");

    public LimboVindicatorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4F;
        this.addLayer(new LimboGlowLayer<>(this, GLOW));
    }

    @Override
    public ResourceLocation getTextureLocation(Vindicator entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Vindicator entity, PoseStack pose, float partialTick) {
        super.scale(entity, pose, partialTick);
        pose.scale(2.0F, 2.0F, 2.0F); // 2x, matching the baked 2x hitbox
    }
}
