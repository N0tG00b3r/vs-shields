package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.VSShieldsMod
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity

/**
 * Tactical Goggles 3D model — Curios head slot renderer.
 * Geometry converted from googles.java (Blockbench 5.0.7, 64×64).
 *
 * Coordinate transforms applied:
 * - bone group (child of Head pivot 5,14,0): X+5, Y-6, Z
 * - bb_main group (pivot 0,24,0): X, Y+4, Z
 * - Head base box (player head shape) skipped to avoid z-fighting.
 */
class TacticalGogglesModel<T : LivingEntity>(root: ModelPart) : HumanoidModel<T>(root) {

    companion object {
        val LAYER_LOCATION = ModelLayerLocation(
            ResourceLocation(VSShieldsMod.MOD_ID, "tactical_goggles"), "main"
        )

        @JvmStatic
        fun createBodyLayer(): LayerDefinition {
            val mesh = MeshDefinition()
            val parts = mesh.root

            // All goggles geometry in "head" part for automatic head rotation tracking.
            // Brim (texOffs 0,0 — 10×1×10) and upper plate (texOffs 0,11 — 8×1×8) removed:
            // their UV overlaps with the skipped 8×8×8 head box, causing artifacts on the crown.
            parts.addOrReplaceChild("head",
                CubeListBuilder.create()
                    // === bone group (X+5, Y-6, Z) — without brim, upper plate, and side panels ===
                    // right side panel 1×7×9 texOffs(20,20) REMOVED — oversized green/metallic plate
                    // brim 10×1×10 texOffs(0,0) REMOVED — UV conflict with head box
                    // upper plate 8×1×8 texOffs(0,11) REMOVED — UV conflict with head box
                    // left side panel 1×7×9 texOffs(0,20) REMOVED — oversized green/metallic plate
                    .texOffs(32, 18).addBox(-4.0F, -8.0F, -5.0F, 8.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(40, 6).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 2.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(40, 38).addBox(-1.0F, -7.0F, -5.0F, 2.0F, 4.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(32, 11).addBox(-5.0F, -8.0F, 4.0F, 10.0F, 6.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(40, 9).addBox(1.0F, -7.0F, -5.0F, 1.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(44, 9).addBox(-2.0F, -7.0F, -5.0F, 1.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    // cheek guards (2×3×1) REMOVED — extra green/metallic bits below lenses
                    // antenna mount bars (2×2×8) REMOVED — protrusions above head
                    // antenna rod (1×3×1) REMOVED — asymmetric vertical stick
                    .texOffs(40, 0).addBox(2.0F, -7.0F, -6.0F, 2.0F, 4.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(40, 20).addBox(-4.0F, -7.0F, -6.0F, 2.0F, 4.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(40, 26).addBox(-2.0F, -6.0F, -6.0F, 1.0F, 4.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(40, 32).addBox(1.0F, -6.0F, -6.0F, 1.0F, 4.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(40, 43).addBox(-1.0F, -3.0F, -6.0F, 2.0F, 1.0F, 2.0F, CubeDeformation(0.0F))
                    // === bb_main group (X, Y+4, Z) ===
                    .texOffs(19, 19).addBox(2.0F, -7.0F, -6.0F, 2.0F, 4.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(11, 26).addBox(1.0F, -6.0F, -6.0F, 1.0F, 3.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(19, 26).addBox(-1.0F, -5.0F, -6.0F, 2.0F, 1.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(29, 19).addBox(-2.0F, -6.0F, -6.0F, 1.0F, 3.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(1, 26).addBox(-4.0F, -7.0F, -6.0F, 2.0F, 4.0F, 2.0F, CubeDeformation(0.0F))
                    .texOffs(10, 31).addBox(-1.0F, -4.0F, -5.0F, 2.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(18, 29).addBox(-4.0F, -3.0F, -5.0F, 3.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(18, 29).addBox(1.0F, -3.0F, -5.0F, 3.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(16, 31).addBox(2.0F, -8.0F, -5.0F, 2.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(16, 31).addBox(-4.0F, -8.0F, -5.0F, 2.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(10, 31).addBox(-1.0F, -6.0F, -5.0F, 2.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(22, 31).addBox(-2.0F, -7.0F, -5.0F, 1.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(22, 31).addBox(1.0F, -7.0F, -5.0F, 1.0F, 1.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(28, 24).addBox(4.0F, -8.0F, -5.0F, 1.0F, 6.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(28, 24).addBox(-5.0F, -8.0F, -5.0F, 1.0F, 6.0F, 1.0F, CubeDeformation(0.0F))
                    .texOffs(0, 16).addBox(-5.0F, -5.0F, -4.0F, 1.0F, 1.0F, 8.0F, CubeDeformation(0.0F))
                    .texOffs(0, 16).addBox(4.0F, -5.0F, -4.0F, 1.0F, 1.0F, 8.0F, CubeDeformation(0.0F))
                    .texOffs(18, 16).addBox(-4.0F, -5.0F, 4.0F, 8.0F, 1.0F, 1.0F, CubeDeformation(0.0F)),
                PartPose.ZERO
            )

            // HumanoidModel requires all standard parts — leave empty for head-only model
            parts.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO)
            parts.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO)
            parts.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO)
            parts.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO)
            parts.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO)
            parts.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO)

            return LayerDefinition.create(mesh, 64, 64)
        }
    }
}
