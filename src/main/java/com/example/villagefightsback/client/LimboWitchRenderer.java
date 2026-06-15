package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Witch;

/** Limbo Witch: the vanilla witch renderer (keeps the held-potion layer), re-skinned black-and-white, scaled
 *  2x to match the baked hitbox, with glowing white eyes/markings in the dark. */
public class LimboWitchRenderer extends WitchRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_witch.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_witch_glow.png");

    public LimboWitchRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4F;
        this.addLayer(new LimboGlowLayer<>(this, GLOW));
    }

    @Override
    public ResourceLocation getTextureLocation(Witch entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Witch entity, PoseStack pose, float partialTick) {
        super.scale(entity, pose, partialTick); // keep the witch's 0.9375 baseline...
        pose.scale(2.0F, 2.0F, 2.0F);           // ...then 2x on top, matching the baked 2x hitbox
    }
}
