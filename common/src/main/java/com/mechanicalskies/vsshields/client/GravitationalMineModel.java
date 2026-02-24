package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.VSShieldsMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 3D entity model for the GravitationalMineEntity.
 * Geometry from Landmine.java (Blockbench 5.0.7 export).
 * Texture: vs_shields:textures/entity/gravitational_mine.png (128×128)
 */
public class GravitationalMineModel<T extends Entity> extends EntityModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(VSShieldsMod.MOD_ID, "gravitational_mine"), "main");

    private final ModelPart root;

    public GravitationalMineModel(ModelPart root) {
        this.root = root.getChild("root");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild("root",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0F, -10.0F, -5.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 20).addBox(-4.0F, -11.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 38).addBox(-3.0F, -12.0F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(50, 28).addBox(-2.0F, -13.0F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 29).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 0).addBox(-3.0F, 1.0F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(50, 33).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 7).addBox(-4.0F, -9.0F, 5.0F, 8.0F, 8.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(50, 38).addBox(-3.0F, -8.0F, 6.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(52, 52).addBox(-2.0F, -7.0F, 7.0F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 45).addBox(-4.0F, -9.0F, -6.0F, 8.0F, 8.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(50, 45).addBox(-3.0F, -8.0F, -7.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 54).addBox(-2.0F, -7.0F, -8.0F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 20).addBox(-6.0F, -9.0F, -4.0F, 1.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(18, 45).addBox(-7.0F, -8.0F, -3.0F, 1.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 52).addBox(-8.0F, -7.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 36).addBox(5.0F, -9.0F, -4.0F, 1.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(50, 16).addBox(6.0F, -8.0F, -3.0F, 1.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(42, 52).addBox(7.0F, -7.0F, -2.0F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 21.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Bobbing and rotation handled by entity tick logic — no skeletal animation needed
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
