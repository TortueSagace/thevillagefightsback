package com.example.villagefightsback.client;

import java.lang.reflect.Field;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
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
 * Renders ONE green emerald spike (the Limbo-helmet spike recoloured) on top of an emerald mob's head, like a
 * single horn. The 18 emerald mobs use ~18 different vanilla models, so the head bone is resolved generically:
 * a {@link HeadedModel}'s {@code getHead()}, else a {@code ModelPart} field whose name contains "head" (cow,
 * pig, horse, ...), else a {@link HierarchicalModel} root child named "head". The spike is anchored to that bone
 * (so it follows head yaw/pitch) and raised by a per-mob {@code headTopY} offset to sit on the head's crown.
 * Mobs with no separable head bone (fish, phantom) resolve to null and simply get no spike.
 */
@OnlyIn(Dist.CLIENT)
public class EmeraldSpikeLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation TEX =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/emerald_head_spike.png");

    private final ModelPart spike;
    private final float headTopY; // pixels (negative = up) of the head crown, relative to the head bone origin
    private ModelPart head;
    private boolean resolved;

    public EmeraldSpikeLayer(RenderLayerParent<T, M> parent, EntityModelSet models, float headTopY) {
        super(parent);
        this.spike = models.bakeLayer(TheVillageFightsBackClient.EMERALD_SPIKE);
        this.headTopY = headTopY;
    }

    /** A single tapered 2x6x2 spike rising from y=0 (base) to y=-6 (tip); UV matches the helmet spike at (0,0). */
    public static LayerDefinition createSpikeLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("spike",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        ModelPart h = resolveHead();
        if (h == null) return;
        pose.pushPose();
        h.translateAndRotate(pose);                 // follow the head bone
        pose.translate(0.0F, headTopY / 16.0F, 0.0F); // raise the spike base to the head crown
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX));
        spike.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Resolve (and cache) the head bone of whatever model this layer is attached to. */
    private ModelPart resolveHead() {
        if (resolved) return head;
        resolved = true;
        EntityModel<T> model = getParentModel();
        if (model instanceof HeadedModel hm) {
            head = hm.getHead();
            return head;
        }
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ModelPart.class.isAssignableFrom(f.getType()) && f.getName().toLowerCase().contains("head")) {
                    try {
                        f.setAccessible(true);
                        head = (ModelPart) f.get(model);
                        return head;
                    } catch (Exception ignored) { /* try the next candidate */ }
                }
            }
        }
        if (model instanceof HierarchicalModel<?> hier) {
            try {
                head = hier.root().getChild("head");
            } catch (Exception ignored) { /* no head child -> null */ }
        }
        return head;
    }
}
