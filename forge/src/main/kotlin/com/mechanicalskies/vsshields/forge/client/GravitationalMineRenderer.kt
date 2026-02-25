package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.client.GravitationalMineModel
import com.mechanicalskies.vsshields.entity.GravitationalMineEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

class GravitationalMineRenderer(ctx: EntityRendererProvider.Context)
    : EntityRenderer<GravitationalMineEntity>(ctx) {

    private val model = GravitationalMineModel<GravitationalMineEntity>(
        ctx.bakeLayer(GravitationalMineModel.LAYER_LOCATION))
    private val texture = ResourceLocation("vs_shields", "textures/entity/gravitational_mine.png")

    override fun render(
        entity: GravitationalMineEntity,
        yaw: Float, partialTick: Float,
        poseStack: PoseStack,
        buf: MultiBufferSource,
        packedLight: Int
    ) {
        poseStack.pushPose()
        if (entity.getPhase() == GravitationalMineEntity.Phase.ARMED) {
            // Smooth Y-axis spin
            poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 2f))
            // Visual bob: entity position is stable, bob is purely cosmetic here
            val bobOffset = Math.sin((entity.tickCount + partialTick) * 0.1) * 0.04
            poseStack.translate(0.0, bobOffset, 0.0)
        }
        // Standard MC EntityModel transform: flip X+Y (keeps Z), then pull model up
        // to align with entity feet. Root is at y=21 px → translate = -21/16 = -1.3125
        poseStack.scale(-1.0f, -1.0f, 1.0f)
        poseStack.translate(0.0, -21.0 / 16.0, 0.0)
        model.setupAnim(entity, 0f, 0f, entity.tickCount + partialTick, 0f, 0f)
        // entityCutout (with backface culling) avoids z-fighting flicker during rotation
        model.renderToBuffer(poseStack,
            buf.getBuffer(RenderType.entityCutout(texture)),
            packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f)
        poseStack.popPose()
        super.render(entity, yaw, partialTick, poseStack, buf, packedLight)
    }

    override fun getTextureLocation(entity: GravitationalMineEntity) = texture
}
