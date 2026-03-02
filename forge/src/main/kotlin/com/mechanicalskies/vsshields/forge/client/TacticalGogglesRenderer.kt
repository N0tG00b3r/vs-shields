package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.VSShieldsMod
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.model.EntityModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import top.theillusivec4.curios.api.SlotContext
import top.theillusivec4.curios.api.client.ICurioRenderer

/**
 * Renders the Tactical Goggles 3D model on the player's head (Curios head slot).
 * Uses HumanoidModel pattern with followBodyRotations for automatic head tracking.
 */
class TacticalGogglesRenderer : ICurioRenderer {

    private var model: TacticalGogglesModel<LivingEntity>? = null

    override fun <T : LivingEntity, M : EntityModel<T>> render(
        stack: ItemStack,
        slotContext: SlotContext,
        poseStack: PoseStack,
        renderLayerParent: RenderLayerParent<T, M>,
        renderTypeBuffer: MultiBufferSource,
        light: Int,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTicks: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float
    ) {
        val entity = slotContext.entity() ?: return

        // Lazy-init model from baked layer
        if (model == null) {
            model = TacticalGogglesModel(
                Minecraft.getInstance().entityModels
                    .bakeLayer(TacticalGogglesModel.LAYER_LOCATION)
            )
        }

        val m = model ?: return

        // Copy head/body rotations from the player model
        ICurioRenderer.followBodyRotations(entity, m)

        val vertexConsumer = renderTypeBuffer.getBuffer(
            RenderType.entityCutoutNoCull(TEXTURE)
        )
        m.renderToBuffer(
            poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, 1.0F
        )
    }

    companion object {
        private val TEXTURE = ResourceLocation(
            VSShieldsMod.MOD_ID, "textures/models/armor/tactical_goggles_layer.png"
        )
    }
}
