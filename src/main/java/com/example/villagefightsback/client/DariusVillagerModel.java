package com.example.villagefightsback.client;

import com.example.villagefightsback.GeneralDariusEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A standard humanoid model with the iconic villager NOSE grafted onto the head. General Darius is a
 * {@code Monster}, so the vanilla {@code ZombieVillagerModel} (bounded to {@code Zombie}) can't be used; this
 * gives him the same villager face (nose pokes out past the helmet) while staying a plain {@link HumanoidModel}
 * so the Limbo armour renders through the {@code HumanoidArmorLayer}. The nose UV (24,0) matches the recoloured
 * vanilla zombie-villager texture used as his skin.
 */
public class DariusVillagerModel extends HumanoidModel<GeneralDariusEntity> {
    public DariusVillagerModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
