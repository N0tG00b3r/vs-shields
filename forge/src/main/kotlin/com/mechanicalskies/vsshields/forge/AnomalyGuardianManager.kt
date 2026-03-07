package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.anomaly.AnomalyInstance
import com.mechanicalskies.vsshields.config.ShieldConfig
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.common.shipObjectWorld

/**
 * Manages spawning of anomaly guardian mobs on the island.
 * Spawns Enderman (50%), Phantom (35%), Shulker (15%) with custom NBT.
 * Implements spawn escalation over time.
 */
object AnomalyGuardianManager {

    private val LOGGER = org.slf4j.LoggerFactory.getLogger("AnomalyGuardianManager")
    private const val GUARDIAN_TAG = "anomaly_guardian"

    // Spawn escalation state
    private var spawnWaveCount = 0
    private var lastSpawnTick = 0L
    private var currentSpawnInterval = 0
    private var currentMaxGuardians = 0

    fun tick(level: ServerLevel, anomaly: AnomalyInstance) {
        val config = ShieldConfig.get().anomaly
        val currentTick = level.gameTime

        // Initialize escalation on first tick
        if (currentSpawnInterval == 0) {
            currentSpawnInterval = config.guardianSpawnInterval
            currentMaxGuardians = config.maxGuardians
        }

        // Check spawn interval
        if (currentTick - lastSpawnTick < currentSpawnInterval) return
        lastSpawnTick = currentTick

        // Only spawn guardians when at least one player is within spawn radius
        // This prevents mob accumulation when the island is in unloaded chunks
        val playerSearchBox = AABB(
            anomaly.worldX - config.guardianSpawnRadius,
            anomaly.worldY - config.guardianSpawnRadius,
            anomaly.worldZ - config.guardianSpawnRadius,
            anomaly.worldX + config.guardianSpawnRadius,
            anomaly.worldY + config.guardianSpawnRadius,
            anomaly.worldZ + config.guardianSpawnRadius
        )
        val hasNearbyPlayer = level.players().any { playerSearchBox.contains(it.x, it.y, it.z) }
        if (!hasNearbyPlayer) return

        // Count existing guardians near island
        val searchBox = AABB(
            anomaly.worldX - config.guardianSpawnRadius,
            anomaly.worldY - config.guardianSpawnRadius,
            anomaly.worldZ - config.guardianSpawnRadius,
            anomaly.worldX + config.guardianSpawnRadius,
            anomaly.worldY + config.guardianSpawnRadius,
            anomaly.worldZ + config.guardianSpawnRadius
        )
        val existing = level.getEntities(null, searchBox).count { it.tags.contains(GUARDIAN_TAG) }
        if (existing >= currentMaxGuardians) return

        // Pick spawn position on island surface using worldAABB
        val spawnPos = findSpawnPosition(level, anomaly) ?: return

        // Roll mob type: 55% Enderman, 40% Phantom, 5% Shulker
        val roll = Math.random()
        when {
            roll < 0.55 -> spawnEnderman(level, spawnPos, anomaly)
            roll < 0.95 -> spawnPhantom(level, spawnPos, anomaly)
            else -> spawnShulker(level, spawnPos)
        }

        // Escalation
        spawnWaveCount++
        if (spawnWaveCount % 3 == 0 && currentSpawnInterval > config.guardianSpawnInterval / 2) {
            currentSpawnInterval = (currentSpawnInterval * 0.9).toInt()
            LOGGER.debug("[Guardians] Escalation: spawn interval reduced to {} ticks", currentSpawnInterval)
        }
        if (spawnWaveCount >= 5 && currentMaxGuardians < config.maxGuardians * 2) {
            currentMaxGuardians = config.maxGuardians + (spawnWaveCount / 5) * 2
            currentMaxGuardians = currentMaxGuardians.coerceAtMost(config.maxGuardians * 2)
            LOGGER.debug("[Guardians] Escalation: max guardians increased to {}", currentMaxGuardians)
        }
    }

    private fun spawnEnderman(level: ServerLevel, pos: BlockPos, anomaly: AnomalyInstance) {
        val enderman = EntityType.ENDERMAN.create(level) ?: return
        enderman.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        enderman.addTag(GUARDIAN_TAG)
        setMobPersistent(enderman)
        // Visual flavor: carry aetheric stone
        try {
            val stoneState = com.mechanicalskies.vsshields.registry.ModBlocks.AETHERIC_STONE.get().defaultBlockState()
            (enderman as? EnderMan)?.setCarriedBlock(stoneState)
        } catch (_: Exception) {}
        // Make enderman aggressive — target nearest player without needing eye contact
        if (enderman is EnderMan) {
            enderman.targetSelector.addGoal(1,
                NearestAttackableTargetGoal(enderman, Player::class.java, true, false))
        }
        level.addFreshEntity(enderman)
        LOGGER.debug("[Guardians] Spawned Enderman at {}", pos)
    }

    private fun spawnPhantom(level: ServerLevel, pos: BlockPos, anomaly: AnomalyInstance) {
        val phantom = EntityType.PHANTOM.create(level) ?: return
        phantom.setPos(pos.x + 0.5, pos.y + 5.0, pos.z + 0.5)
        phantom.addTag(GUARDIAN_TAG)
        setMobPersistent(phantom)
        // Set size to medium (2)
        if (phantom is Phantom) {
            phantom.phantomSize = 2
        }
        // Fire resistance so phantoms don't burn in daylight
        phantom.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, Int.MAX_VALUE, 0, false, false))
        level.addFreshEntity(phantom)
        LOGGER.debug("[Guardians] Spawned Phantom at {}", pos)
    }

    private fun spawnShulker(level: ServerLevel, pos: BlockPos) {
        val shulker = EntityType.SHULKER.create(level) ?: return
        shulker.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        shulker.addTag(GUARDIAN_TAG)
        setMobPersistent(shulker)
        // Purple color (5)
        if (shulker is Shulker) {
            val tag = CompoundTag()
            shulker.saveWithoutId(tag)
            tag.putByte("Color", 5.toByte())
            shulker.load(tag)
        }
        level.addFreshEntity(shulker)
        LOGGER.debug("[Guardians] Spawned Shulker at {}", pos)
    }

    /**
     * Find spawn position using the ship's worldAABB instead of block-state scanning.
     * Block states are in shipyard space, but worldAABB gives actual world coordinates.
     */
    private fun findSpawnPosition(level: ServerLevel, anomaly: AnomalyInstance): BlockPos? {
        val ship = level.shipObjectWorld.loadedShips.getById(anomaly.shipId) as? LoadedServerShip
            ?: return null

        val worldAABB = ship.worldAABB ?: return null

        // Shrink XZ bounds by 20% to avoid spawning at the edge
        val xRange = (worldAABB.maxX() - worldAABB.minX()) * 0.8
        val zRange = (worldAABB.maxZ() - worldAABB.minZ()) * 0.8
        val xCenter = (worldAABB.maxX() + worldAABB.minX()) / 2.0
        val zCenter = (worldAABB.maxZ() + worldAABB.minZ()) / 2.0

        for (attempt in 0 until 10) {
            val x = xCenter + (Math.random() - 0.5) * xRange
            val z = zCenter + (Math.random() - 0.5) * zRange
            // Spawn on top of the island
            val y = worldAABB.maxY() + 1.0

            return BlockPos(x.toInt(), y.toInt(), z.toInt())
        }
        return null
    }

    /**
     * Set PersistenceRequired on a mob via NBT (field is private in Mob).
     */
    private fun setMobPersistent(mob: net.minecraft.world.entity.Mob) {
        val tag = CompoundTag()
        mob.saveWithoutId(tag)
        tag.putBoolean("PersistenceRequired", true)
        mob.load(tag)
    }

    /**
     * Kill all guardians near the anomaly (called during dissolution).
     */
    fun cleanupGuardians(level: ServerLevel, anomaly: AnomalyInstance) {
        val radius = ShieldConfig.get().anomaly.guardianSpawnRadius + 20.0
        val searchBox = AABB(
            anomaly.worldX - radius, anomaly.worldY - radius, anomaly.worldZ - radius,
            anomaly.worldX + radius, anomaly.worldY + radius, anomaly.worldZ + radius
        )
        val guardians = level.getEntities(null, searchBox).filter { it.tags.contains(GUARDIAN_TAG) }
        for (entity in guardians) {
            entity.discard()
        }
        LOGGER.info("[Guardians] Cleaned up {} guardians", guardians.size)
    }

    /**
     * Reset escalation state (called on anomaly despawn).
     */
    fun reset() {
        spawnWaveCount = 0
        lastSpawnTick = 0
        currentSpawnInterval = 0
        currentMaxGuardians = 0
    }
}
