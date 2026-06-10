package com.example.villagergunners.client;

import com.example.villagergunners.EmeraldgerEntity;
import com.example.villagergunners.VillagerGunners;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * Renders the Emeraldger as a villager that is (a) scaled to its HP-derived size and (b) wears an emissive
 * layer of shiny green eyes (fullbright, like spider/enderman eyes, so they glow in the dark). Everything
 * else -- body, held Alien Slasher (vanilla crossed-arms layer) -- is the normal villager appearance.
 */
public class EmeraldgerRenderer extends VillagerRenderer {
    public EmeraldgerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new GreenEyesLayer(this));
    }

    @Override
    protected void scale(Villager entity, PoseStack pose, float partialTick) {
        float s = entity instanceof EmeraldgerEntity em ? em.getEmeraldgerScale() : 1.0F;
        pose.scale(s, s, s);
    }

    /** Fullbright green-eyes overlay mapped onto the villager head UV. */
    private static class GreenEyesLayer extends EyesLayer<Villager, VillagerModel<Villager>> {
        private static final RenderType EYES = RenderType.eyes(
                new ResourceLocation(VillagerGunners.MODID, "textures/entity/emeraldger_eyes.png"));

        GreenEyesLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
