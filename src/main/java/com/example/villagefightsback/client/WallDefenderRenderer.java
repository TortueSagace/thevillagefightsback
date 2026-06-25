package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.WallDefenderEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders the wall defender as a spinning conduit-like core. */
public class WallDefenderRenderer extends MobRenderer<WallDefenderEntity, WallDefenderModel> {
    private static final ResourceLocation TEXTURE =
            TheVillageFightsBack.id("textures/entity/wall_defender.png");
    private static final ResourceLocation TEXTURE_STAGE4 =
            TheVillageFightsBack.id("textures/entity/wall_defender_stage4.png");

    public WallDefenderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WallDefenderModel(ctx.bakeLayer(TheVillageFightsBackClient.WALL_DEFENDER_LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(WallDefenderEntity entity) {
        return entity.isStage4Visual() ? TEXTURE_STAGE4 : TEXTURE;
    }
}
