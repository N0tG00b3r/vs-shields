package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import kotlin.random.Random

/**
 * Applies a camera shake effect while the player is riding a VS2 boarding pod ship
 * in the DRILLING phase. Intensity decreases linearly as the drill timer
 * counts down from DRILL_TOTAL_TICKS to 0.
 *
 * Also forces first-person view while riding a [CockpitSeatEntity] so the camera
 * doesn't clip through pod geometry in third-person mode. The previous camera type
 * is restored on dismount.
 */
object DrillShakeHandler {

    private var savedCameraType: CameraType? = null
    private var wasRidingPod = false

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val isRiding = player.vehicle is CockpitSeatEntity

        if (isRiding) {
            if (!wasRidingPod) {
                // Just mounted — save the current camera type and switch to first-person
                savedCameraType = mc.options.cameraType
                wasRidingPod = true
            }
            if (mc.options.cameraType != CameraType.FIRST_PERSON) {
                mc.options.cameraType = CameraType.FIRST_PERSON
            }
        } else if (wasRidingPod) {
            // Just dismounted — restore previous camera type
            wasRidingPod = false
            val saved = savedCameraType
            if (saved != null && saved != CameraType.FIRST_PERSON) {
                mc.options.cameraType = saved
            }
            savedCameraType = null
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
