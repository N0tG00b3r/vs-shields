package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.anomaly.ClientAnomalyData
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes

/**
 * Client-side particle effects for anomaly events.
 * Called from CLIENT_POST tick in VSShieldsModClient.
 */
object AnomalyParticleHandler {

    private const val BEAM_DURATION_TICKS = 600L // 30 seconds
    private const val BEAM_PARTICLES_PER_TICK = 20
    private const val BEAM_MIN_Y = 0.0
    private const val BEAM_MAX_Y = 320.0

    private const val AMBIENT_RADIUS = 100.0
    private const val AMBIENT_PARTICLES_PER_TICK = 4

    fun tick() {
        val mc = Minecraft.getInstance() ?: return
        val level = mc.level ?: return
        val player = mc.player ?: return
        val gameTick = level.gameTime

        if (!ClientAnomalyData.exists()) return

        val ax = ClientAnomalyData.getWorldX()
        val ay = ClientAnomalyData.getWorldY()
        val az = ClientAnomalyData.getWorldZ()

        // === Spawn beam ===
        if (ClientAnomalyData.getSpawnBeamEndTick() == -1L) {
            // First tick after spawn: resolve to absolute tick
            ClientAnomalyData.setSpawnBeamEndTick(gameTick + BEAM_DURATION_TICKS)
        }
        if (ClientAnomalyData.isSpawnBeamActive(gameTick)) {
            val rand = level.random
            for (i in 0 until BEAM_PARTICLES_PER_TICK) {
                val y = BEAM_MIN_Y + rand.nextDouble() * (BEAM_MAX_Y - BEAM_MIN_Y)
                val ox = (rand.nextDouble() - 0.5) * 1.5
                val oz = (rand.nextDouble() - 0.5) * 1.5
                level.addParticle(ParticleTypes.END_ROD,
                    ax + ox, y, az + oz,
                    0.0, 0.3, 0.0)
                if (i % 3 == 0) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        ax + ox, y, az + oz,
                        (rand.nextDouble() - 0.5) * 0.1, 0.2, (rand.nextDouble() - 0.5) * 0.1)
                }
            }
        }

        // === Ambient motes (within 100 blocks) ===
        val dx = player.x - ax
        val dz = player.z - az
        val distSq = dx * dx + dz * dz
        if (distSq <= AMBIENT_RADIUS * AMBIENT_RADIUS) {
            val rand = level.random
            for (i in 0 until AMBIENT_PARTICLES_PER_TICK) {
                val px = ax + (rand.nextDouble() - 0.5) * 60.0
                val py = ay + (rand.nextDouble() - 0.5) * 30.0
                val pz = az + (rand.nextDouble() - 0.5) * 60.0
                if (i % 2 == 0) {
                    level.addParticle(ParticleTypes.WITCH,
                        px, py, pz,
                        0.0, 0.02, 0.0)
                } else {
                    level.addParticle(ParticleTypes.PORTAL,
                        px, py, pz,
                        (rand.nextDouble() - 0.5) * 0.5,
                        rand.nextDouble() * 0.2,
                        (rand.nextDouble() - 0.5) * 0.5)
                }
            }
        }

        // === Pulse shockwave ===
        if (ClientAnomalyData.getPulseShakeTicks() == 19) {
            // First tick of pulse — emit burst ring
            val rand = level.random
            for (i in 0 until 40) {
                val angle = Math.PI * 2.0 * i / 40.0
                val r = 5.0
                val px = ax + Math.cos(angle) * r
                val pz = az + Math.sin(angle) * r
                val vx = Math.cos(angle) * 0.8
                val vz = Math.sin(angle) * 0.8
                level.addParticle(ParticleTypes.SONIC_BOOM,
                    px, ay + 1.0, pz,
                    vx, 0.0, vz)
            }
        }

        // === Warning shimmer (WARNING phase, within 100 blocks) ===
        val phase = ClientAnomalyData.getAnomalyPhase()
        if (phase == ClientAnomalyData.PHASE_WARNING && distSq <= AMBIENT_RADIUS * AMBIENT_RADIUS) {
            val rand = level.random
            for (i in 0 until 7) {
                val px = ax + (rand.nextDouble() - 0.5) * 40.0
                val py = ay + (rand.nextDouble() - 0.5) * 20.0
                val pz = az + (rand.nextDouble() - 0.5) * 40.0
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    px, py, pz,
                    (rand.nextDouble() - 0.5) * 0.3,
                    rand.nextDouble() * 0.2,
                    (rand.nextDouble() - 0.5) * 0.3)
            }
        }

        // === Dissolution smoke (DISSOLVING phase, within 100 blocks) ===
        if (phase == ClientAnomalyData.PHASE_DISSOLVING && distSq <= AMBIENT_RADIUS * AMBIENT_RADIUS) {
            val rand = level.random
            for (i in 0 until 10) {
                val px = ax + (rand.nextDouble() - 0.5) * 30.0
                val py = ay + (rand.nextDouble() - 0.5) * 15.0
                val pz = az + (rand.nextDouble() - 0.5) * 30.0
                if (i % 3 == 0) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE,
                        px, py, pz,
                        0.0, 0.05, 0.0)
                } else {
                    level.addParticle(ParticleTypes.SMOKE,
                        px, py, pz,
                        (rand.nextDouble() - 0.5) * 0.1,
                        0.03,
                        (rand.nextDouble() - 0.5) * 0.1)
                }
            }
        }
    }
}
