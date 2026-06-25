package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ravager;

/** Limbo Ravager: the vanilla ravager renderer, re-skinned black-and-white, scaled 2x (huge) to match the
 *  baked hitbox, with glowing white eyes/markings in the dark. */
public class LimboRavagerRenderer extends RavagerRenderer {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/limbo_ravager.png");
    private static final ResourceLocation GLOW =
            TheVillageFightsBack.id("textures/entity/limbo_ravager_glow.png");

    public LimboRavagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 3.0F;
        this.addLayer(new LimboGlowLayer<>(this, GLOW));
    }

    @Override
    public ResourceLocation getTextureLocation(Ravager entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Ravager entity, PoseStack pose, float partialTick) {
        super.scale(entity, pose, partialTick);
        pose.scale(2.0F, 2.0F, 2.0F); // 2x, matching the baked 2x hitbox
    }
}
