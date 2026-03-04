package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.anomaly.AnomalyGenerator
import com.mechanicalskies.vsshields.anomaly.AnomalyInstance
import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mechanicalskies.vsshields.network.ModNetwork
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3i
import org.slf4j.LoggerFactory
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

/**
 * Forge-side spawner for Aetheric Anomaly islands.
 *
 * Flow (no single-tick bottleneck):
 *   spawn()        → launch async AnomalyGenerator.generate() on a worker thread
 *   tick (waiting)  → poll CompletableFuture until generation completes
 *   tick (ship)     → create empty VS2 ship, preload shipyard chunks
 *   ticks 1..N      → place blocks directly in shipyard space (flag 0, 500/tick)
 *   final tick       → set ship dynamic, attach AnomalyIslandControl
 *
 * Key design: blocks are placed DIRECTLY into shipyard space, bypassing
 * assembleToShipFull entirely. This eliminates:
 *   - StructureTemplate snapshot (O(N) block reads + NBT serialization)
 *   - Two-pass block removal (O(N × neighbors))
 *   - Single-tick assembleToShipFull spike
 *
 * Mass and inertia are updated incrementally by VS2's MixinLevelChunk hook
 * (onSetBlock) — no batch recomputation needed.
 */
object AnomalySpawner {

    private val LOGGER = LoggerFactory.getLogger("AnomalySpawner")

    /** Blocks placed per tick during incremental spawn. */
    private const val BLOCKS_PER_TICK = 500

    // --- Async generation state ---
    private var generationFuture: CompletableFuture<AnomalyGenerator.GenerationResult>? = null
    private var generationSeed = 0L

    // --- Incremental spawn state ---
    private var pendingEntries: List<Map.Entry<BlockPos, BlockState>>? = null
    private var pendingIndex = 0
    private var pendingCenterX = 0.0
    private var pendingCenterY = 0.0
    private var pendingCenterZ = 0.0
    private var pendingTotalBlocks = 0

    // --- Direct shipyard placement state ---
    /** Ship ID created before block placement (-1 = no ship). */
    private var pendingShipId: Long = -1
    /** Shipyard-space offset: relativePos + this = shipyard BlockPos. */
    private var syOffsetX = 0
    private var syOffsetY = 0
    private var syOffsetZ = 0

    // Legacy: world-space positions for old flow cleanup
    private val placedWorldPositions = mutableSetOf<BlockPos>()

    /** True while an async generation or incremental placement is in progress. */
    fun isSpawning(): Boolean = generationFuture != null || pendingEntries != null

    fun getPendingCenterX(): Double = pendingCenterX
    fun getPendingCenterY(): Double = pendingCenterY
    fun getPendingCenterZ(): Double = pendingCenterZ
    fun getPendingTotalBlocks(): Int = pendingTotalBlocks

    /**
     * Begin spawning an anomaly island. Launches generation on a background
     * thread via CompletableFuture — server tick is NOT blocked.
     */
    fun spawn(level: ServerLevel, x: Double?, y: Double?, z: Double?,
              config: ShieldConfig.AnomalyConfig): Boolean {
        cancelSpawn(level)

        val random = level.random
        var centerX: Double
        var centerY: Double
        var centerZ: Double

        if (x != null && y != null && z != null) {
            centerX = x
            centerY = y
            centerZ = z
        } else {
            val spawnPos = level.sharedSpawnPos
            val angle = random.nextDouble() * Math.PI * 2
            val radius = config.minSpawnRadius + random.nextInt(
                Math.max(1, config.maxSpawnRadius - config.minSpawnRadius + 1)
            )
            centerX = spawnPos.x + Math.cos(angle) * radius
            centerY = (config.minY + random.nextInt(Math.max(1, config.maxY - config.minY + 1))).toDouble()
            centerZ = spawnPos.z + Math.sin(angle) * radius
        }

        // Clamp to world border
        val margin = (config.maxIslandSize / 2 + 10).toDouble()
        val border = getWorldBorderBounds(level)
        if (border != null) {
            centerX = centerX.coerceIn(border.minX + margin, border.maxX - margin)
            centerZ = centerZ.coerceIn(border.minZ + margin, border.maxZ - margin)
        }

        pendingCenterX = centerX
        pendingCenterY = centerY
        pendingCenterZ = centerZ

        val seed = random.nextLong()
        generationSeed = seed

        // Launch generation on a background thread.
        // AnomalyGenerator.generate() is pure computation (noise + HashMap),
        // no Minecraft API calls — fully thread-safe.
        val configSnapshot = config
        generationFuture = CompletableFuture.supplyAsync {
            AnomalyGenerator.generate(seed, configSnapshot)
        }

        LOGGER.info("[AnomalySpawner] Async generation started (seed={}) target=({}, {}, {})",
            seed, centerX.toInt(), centerY.toInt(), centerZ.toInt())

        return true
    }

    /**
     * Called every tick while isSpawning().
     *
     * Phase 1: Poll async generation. If not done, return null (zero tick cost).
     * Phase 1.5: On generation complete — create empty VS2 ship, preload chunks.
     * Phase 2: Place blocks incrementally in shipyard space (500/tick, flag 0).
     * Phase 3: Finalize ship (set dynamic, attach control, notify clients).
     */
    fun tickSpawn(level: ServerLevel, config: ShieldConfig.AnomalyConfig): AnomalyInstance? {
        // --- Phase 1: Wait for async generation ---
        val future = generationFuture
        if (future != null) {
            if (!future.isDone) return null // Still computing — zero tick cost

            try {
                val result = future.get()
                val blockMap = result.blocks

                if (blockMap.isEmpty()) {
                    LOGGER.error("[AnomalySpawner] Async generation produced 0 blocks! seed={}", generationSeed)
                    clearPending()
                    return null
                }

                // --- Phase 1.5: Create empty VS2 ship ---
                val worldCenter = Vector3i(
                    pendingCenterX.toInt(), pendingCenterY.toInt(), pendingCenterZ.toInt()
                )
                val serverShip = level.shipObjectWorld.createNewShipAtBlock(
                    worldCenter, false, 1.0, level.dimensionId
                )
                pendingShipId = serverShip.id

                // Get shipyard center from ship's transform
                val posInShip = serverShip.transform.positionInShip
                syOffsetX = (posInShip.x() - 0.5).roundToInt()
                syOffsetY = (posInShip.y() - 0.5).roundToInt()
                syOffsetZ = (posInShip.z() - 0.5).roundToInt()

                // Compute block bounds for chunk preloading
                var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
                var minZ = Int.MAX_VALUE; var maxZ = Int.MIN_VALUE
                for ((pos, _) in blockMap) {
                    if (pos.x < minX) minX = pos.x
                    if (pos.x > maxX) maxX = pos.x
                    if (pos.z < minZ) minZ = pos.z
                    if (pos.z > maxZ) maxZ = pos.z
                }

                // Preload shipyard chunks (they're empty air — very fast to generate)
                val minChunkX = (syOffsetX + minX) shr 4
                val maxChunkX = (syOffsetX + maxX) shr 4
                val minChunkZ = (syOffsetZ + minZ) shr 4
                val maxChunkZ = (syOffsetZ + maxZ) shr 4
                for (cx in minChunkX..maxChunkX) {
                    for (cz in minChunkZ..maxChunkZ) {
                        level.getChunk(cx, cz) // Force-load chunk
                    }
                }

                LOGGER.info("[AnomalySpawner] Ship created (id={}), shipyard center=({},{},{}), " +
                    "preloaded {}×{} chunks, {} blocks to place",
                    pendingShipId, syOffsetX, syOffsetY, syOffsetZ,
                    maxChunkX - minChunkX + 1, maxChunkZ - minChunkZ + 1,
                    blockMap.size)

                // Shuffle entries for spatial diversity during placement
                val shuffled = blockMap.entries.toMutableList()
                shuffled.shuffle(java.util.Random(generationSeed))

                pendingEntries = shuffled
                pendingIndex = 0
                pendingTotalBlocks = result.totalBlocks
            } catch (e: Exception) {
                LOGGER.error("[AnomalySpawner] Async generation failed", e)
                clearPending()
                return null
            } finally {
                generationFuture = null
            }
        }

        // --- Phase 2: Place blocks in SHIPYARD space ---
        val entries = pendingEntries ?: return null

        val end = Math.min(pendingIndex + BLOCKS_PER_TICK, entries.size)
        for (i in pendingIndex until end) {
            val (relPos, state) = entries[i]
            val syPos = BlockPos(
                syOffsetX + relPos.x,
                syOffsetY + relPos.y,
                syOffsetZ + relPos.z
            )
            level.setBlock(syPos, state, 2)
        }
        pendingIndex = end

        // --- Phase 3: Finalize when all blocks placed ---
        if (pendingIndex >= entries.size) {
            return finishSpawn(level, config)
        }
        return null
    }

    /**
     * All blocks placed in shipyard. Set ship dynamic, attach physics control.
     * No assembleToShipFull needed — mass/inertia already computed incrementally.
     */
    private fun finishSpawn(level: ServerLevel, config: ShieldConfig.AnomalyConfig): AnomalyInstance? {
        val currentTick = level.gameTime
        val dimensionId = level.dimensionId

        if (pendingShipId <= 0) {
            LOGGER.error("[AnomalySpawner] finishSpawn called with no ship!")
            clearPending()
            return null
        }

        try {
            val loadedShip = level.shipObjectWorld.loadedShips.getById(pendingShipId)
                    as? LoadedServerShip
            if (loadedShip == null) {
                LOGGER.error("[AnomalySpawner] Ship id={} not found after placement!", pendingShipId)
                clearPending()
                return null
            }

            // Attach AnomalyIslandControl — anti-gravity and position holding
            val control = AnomalyIslandControl()
            control.targetX = pendingCenterX
            control.targetY = pendingCenterY
            control.targetZ = pendingCenterZ
            control.phaseOffset = (pendingShipId % 1000).toDouble() / 100.0
            control.antiGravMultiplier = config.antiGravityMultiplier
            control.torqueMag = config.warningTorqueMagnitude
            loadedShip.setAttachment(AnomalyIslandControl::class.java, control)

            LOGGER.info("[AnomalySpawner] AnomalyIslandControl attached, ship dynamic, physics active")

            // Notify clients
            ModNetwork.sendAnomalySpawn(level.server!!, pendingShipId,
                pendingCenterX, pendingCenterY, pendingCenterZ)

            val instance = AnomalyInstance(
                pendingShipId, pendingCenterX, pendingCenterY, pendingCenterZ,
                currentTick, pendingTotalBlocks, dimensionId
            )

            LOGGER.info("[AnomalySpawner] Spawn complete: ship id={}, {} blocks (direct shipyard placement)",
                pendingShipId, pendingTotalBlocks)
            clearPending()
            return instance

        } catch (e: Exception) {
            LOGGER.error("[AnomalySpawner] finishSpawn failed for ship id={}", pendingShipId, e)
            // Try to delete the partial ship
            tryDeleteShip(level, pendingShipId)
            clearPending()
            return null
        }
    }

    /**
     * Cancel an in-progress spawn. Cancels async generation, deletes partial ship.
     */
    fun cancelSpawn(level: ServerLevel?) {
        // Cancel async generation if running
        generationFuture?.cancel(false)
        generationFuture = null

        // Delete partially-built ship if it exists
        if (pendingShipId > 0 && level != null) {
            tryDeleteShip(level, pendingShipId)
        }

        // Legacy: clean up any world-space blocks (safety net)
        if (level != null && placedWorldPositions.isNotEmpty()) {
            LOGGER.info("[AnomalySpawner] Cleaning up {} legacy world blocks", placedWorldPositions.size)
            for (pos in placedWorldPositions) {
                level.removeBlock(pos, false)
            }
        }

        clearPending()
    }

    private fun tryDeleteShip(level: ServerLevel, shipId: Long) {
        try {
            val ship = level.shipObjectWorld.loadedShips.getById(shipId) as? LoadedServerShip
            if (ship != null) {
                ShipAssembler.deleteShip(level, ship, false, false)
                LOGGER.info("[AnomalySpawner] Deleted partial ship id={}", shipId)
            }
        } catch (e: Exception) {
            LOGGER.warn("[AnomalySpawner] Failed to delete partial ship id={}: {}", shipId, e.message)
        }
    }

    private fun clearPending() {
        generationFuture = null
        pendingEntries = null
        pendingIndex = 0
        pendingTotalBlocks = 0
        pendingShipId = -1
        syOffsetX = 0; syOffsetY = 0; syOffsetZ = 0
        placedWorldPositions.clear()
    }

    /**
     * Immediately delete the anomaly ship from the world.
     */
    fun despawn(level: ServerLevel, anomaly: AnomalyInstance) {
        cancelSpawn(level)

        try {
            val ship = level.shipObjectWorld.loadedShips.getById(anomaly.shipId)
                    as? LoadedServerShip
            if (ship != null) {
                ShipAssembler.deleteShip(level, ship, false, false)
                LOGGER.info("[AnomalySpawner] Deleted ship id={}", anomaly.shipId)
            } else {
                LOGGER.warn("[AnomalySpawner] Ship id={} not found for despawn", anomaly.shipId)
            }
        } catch (e: Exception) {
            LOGGER.error("[AnomalySpawner] Failed to despawn ship id={}", anomaly.shipId, e)
        }

        AnomalyPhysicsHandler.reset()

        level.server?.let { ModNetwork.sendAnomalyDespawn(it) }
    }

    /**
     * Check if a VS2 ship with the given ID still exists.
     */
    fun shipExists(level: ServerLevel, shipId: Long): Boolean {
        return level.shipObjectWorld.loadedShips.getById(shipId) != null
    }

    // ── World border integration ────────────────────────────────────────────

    private data class BorderBounds(
        val minX: Double, val maxX: Double,
        val minZ: Double, val maxZ: Double
    )

    private fun getWorldBorderBounds(level: ServerLevel): BorderBounds? {
        try {
            val clazz = Class.forName("com.natamus.worldborder.config.ConfigHandler")
            val enabled = clazz.getField("enableCustomOverworldBorder").get(null)
            if (enabled == true) {
                val posX = (clazz.getField("overworldBorderPositiveX").get(null) as Number).toDouble()
                val negX = (clazz.getField("overworldBorderNegativeX").get(null) as Number).toDouble()
                val posZ = (clazz.getField("overworldBorderPositiveZ").get(null) as Number).toDouble()
                val negZ = (clazz.getField("overworldBorderNegativeZ").get(null) as Number).toDouble()
                LOGGER.debug("[AnomalySpawner] Using Serilum WorldBorder: X=[{},{}] Z=[{},{}]",
                    negX.toInt(), posX.toInt(), negZ.toInt(), posZ.toInt())
                return BorderBounds(negX, posX, negZ, posZ)
            }
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            LOGGER.warn("[AnomalySpawner] Failed to read Serilum WorldBorder config", e)
        }

        val wb = level.worldBorder
        if (wb.size < 5.9E7) {
            return BorderBounds(wb.minX, wb.maxX, wb.minZ, wb.maxZ)
        }

        return null
    }
}
