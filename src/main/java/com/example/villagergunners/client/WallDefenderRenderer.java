package com.example.villagergunners.client;

import com.example.villagergunners.VillagerGunners;
import com.example.villagergunners.WallDefenderEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders the wall defender as a spinning conduit-like core. */
public class WallDefenderRenderer extends MobRenderer<WallDefenderEntity, WallDefenderModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(VillagerGunners.MODID, "textures/entity/wall_defender.png");

    public WallDefenderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new WallDefenderModel(ctx.bakeLayer(VillagerGunnersClient.WALL_DEFENDER_LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(WallDefenderEntity entity) {
        return TEXTURE;
    }
}
