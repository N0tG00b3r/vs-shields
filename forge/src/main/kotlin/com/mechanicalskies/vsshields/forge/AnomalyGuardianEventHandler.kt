package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.anomaly.AnomalyManager
import com.mechanicalskies.vsshields.config.ShieldConfig
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.entity.EntityTeleportEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/**
 * Constrains anomaly guardian behavior:
 * - Enderman teleport fully cancelled (they stay put on the island)
 * - Phantom flight constraining (orbits island, doesn't fly away)
 */
object AnomalyGuardianEventHandler {

    private const val GUARDIAN_TAG = "anomaly_guardian"

    /**
     * Cancel ALL Enderman teleports for anomaly guardians.
     * Vanilla endermen teleport constantly — on the floating island they'd just
     * teleport off the edge and fall into the void.
     */
    @SubscribeEvent
    fun onEnderTeleport(event: EntityTeleportEvent.EnderEntity) {
        val entity = event.entity
        if (!entity.tags.contains(GUARDIAN_TAG)) return
        // Cancel entirely — enderman stays where it is
        event.isCanceled = true
    }

    /**
     * Constrain Phantom flight to orbit around the island.
     */
    @SubscribeEvent
    fun onLivingTick(event: LivingEvent.LivingTickEvent) {
        val entity = event.entity
        if (!entity.tags.contains(GUARDIAN_TAG)) return
        if (entity.level().isClientSide) return

        val anomaly = AnomalyManager.getInstance().active ?: return
        val config = ShieldConfig.get().anomaly

        when (entity) {
            is Phantom -> {
                val center = Vec3(anomaly.worldX, anomaly.worldY, anomaly.worldZ)
                val pos = entity.position()
                val dist = pos.distanceTo(center)

                if (dist > config.phantomOrbitRadius) {
                    // Apply corrective velocity toward center
                    val dir = center.subtract(pos).normalize()
                    val correction = dir.scale(0.15)
                    entity.deltaMovement = entity.deltaMovement.add(correction)
                }
            }
            is EnderMan -> {
                // Keep endermen from walking off the island edge
                val center = Vec3(anomaly.worldX, anomaly.worldY, anomaly.worldZ)
                val pos = entity.position()
                val horizontalDist = Math.sqrt(
                    (pos.x - center.x) * (pos.x - center.x) +
                    (pos.z - center.z) * (pos.z - center.z)
                )
                // If enderman drifts too far horizontally, push it back
                if (horizontalDist > config.endermanTeleportRadius * 0.7) {
                    val dir = Vec3(center.x - pos.x, 0.0, center.z - pos.z).normalize()
                    entity.deltaMovement = entity.deltaMovement.add(dir.scale(0.1))
                }
            }
        }
    }
}
