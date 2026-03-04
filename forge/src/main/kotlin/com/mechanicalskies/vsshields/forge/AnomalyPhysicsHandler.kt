package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.anomaly.AnomalyInstance
import com.mechanicalskies.vsshields.config.ShieldConfig
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.common.shipObjectWorld

/**
 * Game-thread coordinator for anomaly island physics.
 *
 * Does NOT apply forces itself — all physics runs on the physics thread
 * via [AnomalyIslandControl] (a [ShipPhysicsListener] attachment).
 *
 * This handler's only job is:
 *  1. Lazy-create and attach [AnomalyIslandControl] to the ship on first call
 *  2. Update the control's volatile flags each game tick (applyTorque, active)
 *
 * Zero GTPA calls, zero force calculations on the game thread.
 */
object AnomalyPhysicsHandler {

    private val LOGGER = org.slf4j.LoggerFactory.getLogger("AnomalyPhysicsHandler")

    /** Ship ID that currently has an attached control. -1 = none. */
    private var attachedShipId = -1L

    fun applyPhysics(
        level: ServerLevel, anomaly: AnomalyInstance,
        applyTorque: Boolean, config: ShieldConfig.AnomalyConfig
    ) {
        val ship = level.shipObjectWorld.loadedShips.getById(anomaly.shipId)
                as? LoadedServerShip ?: return

        // Lazy-create: attach control if not yet done (first tick after spawn or server restart)
        var control = ship.getAttachment(AnomalyIslandControl::class.java)
        if (control == null) {
            control = AnomalyIslandControl()
            control.targetX = anomaly.worldX
            control.targetY = anomaly.worldY
            control.targetZ = anomaly.worldZ
            control.antiGravMultiplier = config.antiGravityMultiplier
            control.torqueMag = config.warningTorqueMagnitude
            control.driftXZ = 2.5
            control.driftY = 1.5
            control.phaseOffset = (anomaly.shipId % 1000).toDouble() / 100.0
            ship.setAttachment(AnomalyIslandControl::class.java, control)
            attachedShipId = anomaly.shipId
            LOGGER.info("[AnomalyPhysics] Attached control to ship id={}", anomaly.shipId)
        }

        // Update volatile flags — physics thread reads these at 60 Hz
        control.applyTorque = applyTorque
        control.active = true
    }

    /**
     * Trigger a one-time yaw impulse on the anomaly island (extraction torque).
     * Called from game thread when player mines Aether Crystal Ore.
     */
    fun triggerExtractionKick(level: ServerLevel, shipId: Long) {
        val ship = level.shipObjectWorld.loadedShips.getById(shipId)
                as? LoadedServerShip ?: return
        val control = ship.getAttachment(AnomalyIslandControl::class.java) ?: return
        control.extractionKickPending = true
    }

    /**
     * Deactivate the control (sets active=false so physics thread stops applying forces)
     * and clear local state. Called on despawn / server stop.
     */
    fun reset() {
        if (attachedShipId != -1L) {
            LOGGER.info("[AnomalyPhysics] Reset (ship id={})", attachedShipId)
        }
        attachedShipId = -1L
        // Note: the control attachment lives on the ship object and will be GC'd
        // when the ship is deleted. We just clear our local tracking.
    }
}
