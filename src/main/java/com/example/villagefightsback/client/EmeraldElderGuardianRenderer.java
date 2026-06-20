package com.example.villagefightsback.client;

import com.example.villagefightsback.EmeraldElderGuardian;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ElderGuardianRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Guardian;

/**
 * Renders the Emerald Elder Guardian: the vanilla Elder Guardian model with its OWN emerald-faction texture and
 * an emissive (fullbright) layer of glowing emerald eyes, mirroring the Emeraldger's eyes overlay.
 */
public class EmeraldElderGuardianRenderer extends ElderGuardianRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_elder_guardian.png");

    public EmeraldElderGuardianRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        // Anonymous EyesLayer<> infers <ElderGuardian, GuardianModel> from this renderer's RenderLayerParent type.
        final RenderType eyes = RenderType.eyes(
                new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_elder_guardian_eyes.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Guardian entity) {
        return TEXTURE;
    }
}
