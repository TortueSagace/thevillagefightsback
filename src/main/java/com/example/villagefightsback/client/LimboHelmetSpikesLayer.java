package com.example.villagefightsback.client;

import com.example.villagefightsback.ModArmor;
import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only: renders three spikes on top of the head whenever a humanoid wearer (chiefly the player) has
 * the Limbo Helmet equipped. The spikes are a baked {@link ModelPart} sitting on top of the head; in
 * {@link #render} we move into head space exactly like vanilla CustomHeadLayer (via
 * {@code getParentModel().head.translateAndRotate}) so the spikes follow head yaw/pitch.
 *
 * The head is an 8x8x8 box whose top sits at y=-8 in head space (head origin at the neck), so the spikes
 * are built rising upward from y=-8 to y=-13. Generic over LivingEntity + HumanoidModel so it attaches to
 * the player renderers (PlayerModel extends HumanoidModel); the layer no-ops unless the head slot holds the
 * Limbo Helmet.
 */
@OnlyIn(Dist.CLIENT)
public class LimboHelmetSpikesLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TheVillageFightsBack.MODID, "textures/entity/limbo_helmet_spikes.png");

    private final ModelPart spikes;

    public LimboHelmetSpikesLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        this.spikes = models.bakeLayer(TheVillageFightsBackClient.LIMBO_HELMET_SPIKES);
    }

    /** Three tapered spike boxes resting on top of the head (head top is at y=-8, rising to y=-13). */
    public static LayerDefinition createSpikesLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Centre spike (a touch taller), then a left and right spike. Each is a 2x5x2 box.
        root.addOrReplaceChild("center",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -14.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("left",
                CubeListBuilder.create().texOffs(8, 0).addBox(-3.0F, -13.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("right",
                CubeListBuilder.create().texOffs(16, 0).addBox(1.0F, -13.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        if (entity.getItemBySlot(EquipmentSlot.HEAD).getItem() != ModArmor.LIMBO_HELMET.get()) return;

        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        spikes.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
