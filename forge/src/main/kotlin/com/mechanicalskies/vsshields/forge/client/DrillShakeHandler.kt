package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraftforge.client.event.RenderPlayerEvent
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import kotlin.random.Random

/**
 * Applies a camera shake effect while the player is riding a VS2 boarding pod ship
 * in the DRILLING phase. Intensity decreases linearly as the drill timer
 * counts down from DRILL_TOTAL_TICKS to 0.
 *
 * Third-person view (F5) works freely — VS2 handles it natively via
 * ShipMountedToDataProvider on CockpitSeatEntity.
 */
object DrillShakeHandler {

    /**
     * Scales the player's rendered model down while seated in a cockpit so they look
     * physically contained inside the block when viewed by other players.
     * Scale 0.4 × normal height ≈ 0.72 blocks — fits inside the 0.7-high hitbox.
     * Push/pop ensures the scale doesn't leak to subsequent rendering (name tags etc.).
     */
    @SubscribeEvent
    fun onRenderPlayerPre(event: RenderPlayerEvent.Pre) {
        if (event.entity.vehicle is CockpitSeatEntity) {
            event.poseStack.pushPose()
            event.poseStack.scale(0.4f, 0.4f, 0.4f)
        }
    }

    @SubscribeEvent
    fun onRenderPlayerPost(event: RenderPlayerEvent.Post) {
        if (event.entity.vehicle is CockpitSeatEntity) {
            event.poseStack.popPose()
        }
    }

    @SubscribeEvent
    fun onCameraAngles(event: ViewportEvent.ComputeCameraAngles) {
        val player = Minecraft.getInstance().player ?: return
        val seat = player.vehicle as? CockpitSeatEntity ?: return
        if (seat.phase != CockpitSeatEntity.Phase.DRILLING) return
        val timer = seat.drillTimer
        if (timer <= 0) return

        // Intensity ramps from ~1.8 at the start down to 0 at completion
        val progress = Mth.clamp(timer / 40f, 0f, 1f)
        val intensity = progress * 1.8f

        event.yaw   += (Random.nextDouble() - 0.5).toFloat() * intensity
        event.pitch += (Random.nextDouble() - 0.5).toFloat() * intensity
    }
}
