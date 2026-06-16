package com.example.villagefightsback.client;

import com.example.villagefightsback.Factions;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only: overlays an angry villager face on a Guard Villagers guard ({@code guardvillagers:guard})
 * while it is FIGHTING (see {@link AngryFaceClient}). Guard Villagers is a RUNTIME soft-dep, so we cannot
 * import its classes -- this layer is generic over {@link LivingEntity} + {@link HumanoidModel} and is
 * only ever attached to the guard's renderer in {@code TheVillageFightsBackClient.onAddLayers}, so the
 * concrete type does not matter here.
 *
 * The guard uses a standard Steve-like 8x8x8 head, so the overlay texture is laid out on the humanoid
 * head UV (front face only, transparent elsewhere). Same technique as {@link AngryVillagerFaceLayer}:
 * re-render the posed parent model with the overlay texture, grown a hair to avoid z-fighting.
 *
 * Infected / limbo creatures are excluded (they do not have a villager face to scowl with); the mod's own
 * zombie-skinned VindictiveGuardEntity uses the ZombieVillager renderer, not a HumanoidModel guard
 * renderer, so it never reaches this layer.
 */
@OnlyIn(Dist.CLIENT)
public class AngryGuardFaceLayer extends RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> {
    private static final ResourceLocation FACE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/angry_guard_face.png");
    private static final float GROW = 1.01F; // tiny outset so the scowl sits over (not inside) the face

    public AngryGuardFaceLayer(RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, LivingEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        if (Factions.isInfected(entity) || Factions.isLimbo(entity)) return;
        if (!AngryFaceClient.isAngry(entity)) return;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(FACE));
        pose.pushPose();
        pose.scale(GROW, GROW, GROW);
        getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
