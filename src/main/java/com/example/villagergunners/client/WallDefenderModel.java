package com.example.villagergunners.client;

import com.example.villagergunners.WallDefenderEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A floating conduit-like core that spins continuously (like the vanilla conduit, but on land too) and bobs
 * up and down a little.
 */
public class WallDefenderModel extends EntityModel<WallDefenderEntity> {
    private final ModelPart core;

    public WallDefenderModel(ModelPart root) {
        this.core = root.getChild("core");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void setupAnim(WallDefenderEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        core.yRot = ageInTicks * 0.10F;                                  // continuous spin, no water needed
        core.xRot = (float) Math.sin(ageInTicks * 0.05F) * 0.10F;        // slight tumble
        core.y = 12.0F + (float) Math.sin(ageInTicks * 0.10F) * 0.8F;    // gentle float bob
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float r, float g, float b, float a) {
        core.render(pose, buffer, packedLight, packedOverlay, r, g, b, a);
    }
}
