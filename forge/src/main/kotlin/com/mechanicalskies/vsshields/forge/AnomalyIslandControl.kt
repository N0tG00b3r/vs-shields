package com.mechanicalskies.vsshields.forge

import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.world.PhysLevel

/**
 * Physics-thread controller for anomaly floating island.
 *
 * Attached to the VS2 ship via [LoadedServerShip.setAttachment].
 * [physTick] runs at 60 Hz directly on the physics thread — no GTPA,
 * no game-thread force calculations, no torn reads.
 *
 * Communication: game thread sets [applyTorque] / [active] via volatile writes.
 * All other fields are set once at creation and never change.
 *
 * Survives game-thread freezes — physics thread continues applying
 * anti-gravity + spring independently.
 */
class AnomalyIslandControl : ShipPhysicsListener {

    // --- Configuration (set once from game thread at creation) ---
    var targetX = 0.0
    var targetY = 0.0
    var targetZ = 0.0
    var antiGravMultiplier = 1.0
    var torqueMag = 50000.0
    var driftXZ = 2.5
    var driftY = 1.5
    var phaseOffset = 0.0

    // --- Cross-thread (game thread writes, physics thread reads) ---
    @Volatile @JvmField var applyTorque = false
    @Volatile @JvmField var active = true
    @Volatile @JvmField var extractionKickPending = false

    // --- Physics thread only ---
    @Transient private var prevX = Double.NaN
    @Transient private var prevY = Double.NaN
    @Transient private var prevZ = Double.NaN
    @Transient private var physTicks = 0L
    @Transient private val rng = java.util.Random()

    companion object {
        // Spring: ζ = 10 / (2√5) ≈ 2.24 — overdamped, no oscillation
        private const val SPRING_K = 5.0
        private const val DAMPING_B = 10.0
        private const val GRAVITY = 10.0 // m/s² in VS2

        // Lissajous at 60 Hz (same real-time periods as 20 Hz originals)
        // period 40s: 2π / (40 × 60) = 0.002618
        // period 53s: 2π / (53 × 60) = 0.001975
        // period 67s: 2π / (67 × 60) = 0.001563
        private const val FREQ_X = 0.002618
        private const val FREQ_Y = 0.001975
        private const val FREQ_Z = 0.001563
    }

    override fun physTick(physShip: PhysShip, physLevel: PhysLevel) {
        if (!active) return
        physTicks++

        val mass = physShip.mass
        val pos = physShip.transform.positionInWorld
        val cx = pos.x(); val cy = pos.y(); val cz = pos.z()

        // --- Velocity from position delta (60 Hz — 3× more accurate than game thread) ---
        var vx = 0.0; var vy = 0.0; var vz = 0.0
        if (!prevX.isNaN()) {
            vx = (cx - prevX) * 60.0
            vy = (cy - prevY) * 60.0
            vz = (cz - prevZ) * 60.0
        }
        prevX = cx; prevY = cy; prevZ = cz

        // --- Lissajous drift target ---
        val t = physTicks.toDouble()
        val tx = targetX + Math.sin(t * FREQ_X + phaseOffset) * driftXZ
        val ty = targetY + Math.sin(t * FREQ_Y + phaseOffset * 1.7) * driftY
        val tz = targetZ + Math.cos(t * FREQ_Z + phaseOffset * 0.6) * driftXZ

        // --- Spring-damper + anti-gravity ---
        val k = mass * SPRING_K
        val b = mass * DAMPING_B
        val ag = mass * GRAVITY * antiGravMultiplier

        val fx = (tx - cx) * k - vx * b
        val fy = (ty - cy) * k - vy * b + ag
        val fz = (tz - cz) * k - vz * b

        physShip.applyWorldForceToBodyPos(Vector3d(fx, fy, fz), Vector3d())

        // --- WARNING phase: random torque ---
        if (applyTorque) {
            physShip.applyWorldTorque(Vector3d(
                (rng.nextDouble() - 0.5) * 2.0 * torqueMag,
                (rng.nextDouble() - 0.5) * 0.6 * torqueMag,
                (rng.nextDouble() - 0.5) * 2.0 * torqueMag
            ))
        }

        // --- Extraction kick: one-time yaw impulse when player mines ore ---
        if (extractionKickPending) {
            extractionKickPending = false
            val sign = if (rng.nextBoolean()) 1.0 else -1.0
            physShip.applyWorldTorque(Vector3d(0.0, sign * 20000.0, 0.0))
        }
    }
}
