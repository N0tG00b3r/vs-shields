package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.anomaly.AnomalyInstance
import com.mechanicalskies.vsshields.anomaly.AnomalyManager
import com.mechanicalskies.vsshields.config.ShieldConfig
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.shipObjectWorld

/**
 * Applies a distance-based repulsion force to non-anomaly ships that approach
 * the anomaly island. Prevents ships from entering the island zone.
 *
 * Called every 10 ticks from AnomalyManager during ACTIVE and EXTRACTION phases.
 */
object AnomalyShipRepulsion {

    private val LOGGER = org.slf4j.LoggerFactory.getLogger("AnomalyShipRepulsion")

    fun tick(level: ServerLevel, anomaly: AnomalyInstance) {
        val config = ShieldConfig.get().anomaly
        val shipId = anomaly.shipId
        if (shipId <= 0) return

        val anomalyShip = level.shipObjectWorld.loadedShips.getById(shipId) ?: return
        val worldAABB = anomalyShip.worldAABB

        val repulsionRadius = config.repulsionRadius
        val repulsionForce = config.repulsionForce

        // Inflate anomaly AABB by repulsion radius to find nearby ships
        val searchAABB = AABBd(
            worldAABB.minX() - repulsionRadius, worldAABB.minY() - repulsionRadius, worldAABB.minZ() - repulsionRadius,
            worldAABB.maxX() + repulsionRadius, worldAABB.maxY() + repulsionRadius, worldAABB.maxZ() + repulsionRadius
        )

        // Anomaly center of mass
        val anomalyMat = anomalyShip.shipToWorld
        val aComX = anomalyMat.m30()
        val aComY = anomalyMat.m31()
        val aComZ = anomalyMat.m32()

        try {
            val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)

            for (foreign: Ship in level.getShipsIntersecting(searchAABB)) {
                if (foreign.id == shipId) continue
                // Don't repulse boarding pods — they have their own approach logic
                if (PodShipManager.isTrustedPodShip(foreign.id)) continue

                val foreignMat = foreign.shipToWorld
                val fComX = foreignMat.m30()
                val fComY = foreignMat.m31()
                val fComZ = foreignMat.m32()

                // Direction from anomaly center to foreign ship
                var dx = fComX - aComX
                var dy = fComY - aComY
                var dz = fComZ - aComZ
                val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
                if (dist < 1e-6) continue

                // Normalize
                dx /= dist; dy /= dist; dz /= dist

                // Force scales inversely with distance (stronger when closer)
                // At dist=0 → full force, at dist=repulsionRadius → 0
                val halfExtent = Math.max(
                    (worldAABB.maxX() - worldAABB.minX()) / 2.0,
                    (worldAABB.maxZ() - worldAABB.minZ()) / 2.0
                )
                val effectiveRange = halfExtent + repulsionRadius
                if (dist >= effectiveRange) continue

                val ratio = 1.0 - (dist / effectiveRange)
                val forceMag = ratio * ratio * repulsionForce // quadratic falloff

                // Push foreign ship away from anomaly
                gtpa.applyWorldForce(
                    foreign.id,
                    Vector3d(dx * forceMag, dy * forceMag, dz * forceMag),
                    Vector3d(fComX, fComY, fComZ)
                )
            }
        } catch (e: Exception) {
            LOGGER.warn("[Repulsion] Failed to apply repulsion force: {}", e.message)
        }
    }
}
