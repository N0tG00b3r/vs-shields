package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

/**
 * No-op renderer for the invisible [CockpitSeatEntity].
 *
 * The seat entity carries the player camera inside the VS2 boarding pod ship;
 * it has no visual geometry of its own.
 */
class CockpitSeatRenderer(context: EntityRendererProvider.Context) :
        EntityRenderer<CockpitSeatEntity>(context) {

    override fun getTextureLocation(entity: CockpitSeatEntity): ResourceLocation =
        ResourceLocation("vs_shields:textures/entity/cockpit_seat.png")

    override fun render(
        entity: CockpitSeatEntity,
        yaw: Float,
        partial: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int
    ) {
        // Invisible entity — nothing to render
    }
}
