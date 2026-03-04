package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.anomaly.AnomalyInstance
import com.mechanicalskies.vsshields.config.ShieldConfig
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import org.slf4j.LoggerFactory
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.common.shipObjectWorld
import org.joml.Vector3d

/**
 * Handles edge-inward dissolution of anomaly island blocks.
 * Blocks are sorted by distance from centroid (descending) — edges removed first.
 */
object AnomalyDissolver {

    private val LOGGER = LoggerFactory.getLogger("AnomalyDissolver")

    /**
     * Prepare the dissolution order if not yet computed.
     * Scans the ship's blocks in shipyard space, sorts by distance from centroid (descending).
     */
    fun prepareDissolutionOrder(level: ServerLevel, anomaly: AnomalyInstance) {
        if (anomaly.dissolutionOrder.isNotEmpty()) return

        val ship = level.shipObjectWorld.loadedShips.getById(anomaly.shipId)
                as? LoadedServerShip ?: run {
            LOGGER.warn("[AnomalyDissolver] Ship id={} not found, can't prepare dissolution", anomaly.shipId)
            return
        }

        // Get ship AABB in shipyard space
        val shipyard = ship.shipAABB ?: run {
            LOGGER.warn("[AnomalyDissolver] Ship id={} has no AABB", anomaly.shipId)
            return
        }

        // Scan all non-air blocks within the shipyard AABB
        val blocks = mutableListOf<BlockPos>()
        val minX = shipyard.minX().toInt()
        val minY = shipyard.minY().toInt()
        val minZ = shipyard.minZ().toInt()
        val maxX = shipyard.maxX().toInt()
        val maxY = shipyard.maxY().toInt()
        val maxZ = shipyard.maxZ().toInt()

        // Compute centroid
        var cx = 0.0
        var cy = 0.0
        var cz = 0.0
        var count = 0

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    if (!level.getBlockState(pos).isAir) {
                        blocks.add(pos)
                        cx += x
                        cy += y
                        cz += z
                        count++
                    }
                }
            }
        }

        if (count == 0) {
            LOGGER.warn("[AnomalyDissolver] No blocks found in shipyard AABB")
            return
        }

        cx /= count
        cy /= count
        cz /= count

        // Sort by distance from centroid — descending (edges first)
        val centroid = Vector3d(cx, cy, cz)
        blocks.sortByDescending { pos ->
            val dx = pos.x - centroid.x
            val dy = pos.y - centroid.y
            val dz = pos.z - centroid.z
            dx * dx + dy * dy + dz * dz
        }

        anomaly.dissolutionOrder = blocks
        LOGGER.info("[AnomalyDissolver] Prepared {} blocks for dissolution, centroid=({}, {}, {})",
            blocks.size, cx.toInt(), cy.toInt(), cz.toInt())
    }

    /**
     * Remove a batch of blocks per tick during dissolution.
     *
     * @return true when dissolution is complete (all blocks removed)
     */
    fun tickDissolve(level: ServerLevel, anomaly: AnomalyInstance, config: ShieldConfig.AnomalyConfig): Boolean {
        // Prepare if needed
        if (anomaly.dissolutionOrder.isEmpty()) {
            prepareDissolutionOrder(level, anomaly)
            if (anomaly.dissolutionOrder.isEmpty()) return true // nothing to dissolve
        }

        val order = anomaly.dissolutionOrder
        val total = order.size
        val phaseTicks = config.dissolutionPhaseTicks.coerceAtLeast(1)
        val blocksPerTick = ((total + phaseTicks - 1) / phaseTicks).coerceAtLeast(1)

        var removed = 0
        val dissolved = anomaly.dissolvedBlocks
        val startIndex = dissolved
        val endIndex = (dissolved + blocksPerTick).coerceAtMost(total)

        for (i in startIndex until endIndex) {
            val pos = order[i]
            if (!level.getBlockState(pos).isAir) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
                removed++
            }
        }

        anomaly.dissolvedBlocks = endIndex

        if (endIndex >= total) {
            LOGGER.info("[AnomalyDissolver] Dissolution complete. Removed {} blocks total.", endIndex)
            return true
        }
        return false
    }
}
