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
            poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 2f))
        }
        poseStack.scale(1f, -1f, -1f)
        model.setupAnim(entity, 0f, 0f, entity.tickCount + partialTick, 0f, 0f)
        model.renderToBuffer(poseStack,
            buf.getBuffer(RenderType.entityCutoutNoCull(texture)),
            packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f)
        poseStack.popPose()
        super.render(entity, yaw, partialTick, poseStack, buf, packedLight)
    }

    override fun getTextureLocation(entity: GravitationalMineEntity) = texture
}
