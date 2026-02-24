package com.mechanicalskies.vsshields.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import com.mechanicalskies.vsshields.VSShieldsMod;

/**
 * Custom 3D helmet model for the Tactical Netherite Helm.
 * Geometry from tactical_helmet.java (Blockbench 5.0.7 export).
 * Texture: vs_shields:textures/models/armor/tactical_helm_layer_1.png (64×64)
 *
 * Extends HumanoidModel so HumanoidArmorLayer can copy the player's head
 * rotation
 * and render the helmet in the correct position.
 */
public class TacticalHelmModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(VSShieldsMod.MOD_ID, "tactical_helm"), "main");

    public TacticalHelmModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        // Helmet geometry (Blockbench export, PartPose adjusted to (0,0,0) for
        // HumanoidModel head pivot)
        parts.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 11).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(20, 20).addBox(4.0F, -8.0F, -5.0F, 1.0F, 7.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 11).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 20).addBox(-5.0F, -8.0F, -5.0F, 1.0F, 7.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 18).addBox(-4.0F, -8.0F, -5.0F, 8.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 6).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 38).addBox(-1.0F, -7.0F, -5.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 11).addBox(-5.0F, -8.0F, 4.0F, 10.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 9).addBox(1.0F, -7.0F, -5.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(44, 9).addBox(-2.0F, -7.0F, -5.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 46).addBox(2.0F, -3.0F, -5.0F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(6, 46).addBox(-4.0F, -3.0F, -5.0F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 36).addBox(1.0F, -11.0F, -5.0F, 2.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(20, 36).addBox(-3.0F, -11.0F, -5.0F, 2.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(12, 46).addBox(1.0F, -14.0F, -3.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 0).addBox(2.0F, -7.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 20).addBox(-4.0F, -7.0F, -6.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 26).addBox(-2.0F, -6.0F, -6.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 32).addBox(1.0F, -6.0F, -6.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 43).addBox(-1.0F, -3.0F, -6.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // HumanoidModel requires all standard slots; leave them empty for a helmet-only
        // model
        parts.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        parts.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        parts.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        parts.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        parts.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        parts.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }
}
