package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity
import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mechanicalskies.vsshields.item.FrequencyIDCardItem
import com.mechanicalskies.vsshields.shield.ShieldManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipsIntersecting
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Server-side tick handler that physically blocks living entities and foreign VS2 ships
 * from entering the shield zone when solid mode is active.
 */
class ShieldSolidBarrier {

    private val knownInside  = HashMap<Long, HashSet<UUID>>()
    private val knownOutside = HashMap<Long, HashSet<UUID>>()

    /**
     * Tracks which shield ships have already had their initial entity scan.
     * On first activation we grandfather everyone inside; after that, new
     * untracked entities inside are treated as unauthorized (teleport exploit fix).
     */
    private val initializedShields = HashSet<Long>()

    /**
     * Passengers ejected from a Boarding Pod that breached this shield.
     * Maps UUID → game-time tick when trust expires.
     * Entities with an active trust entry are always granted inside status.
     */
    private val trustedUntil = HashMap<UUID, Long>()

    companion object {
        private var instance: ShieldSolidBarrier? = null

        /**
         * Called from [com.mechanicalskies.vsshields.entity.BoardingPodEntity]
         * via the static TrustCallback to grant a boarding pod passenger
         * temporary pass-through for [ticks] game ticks.
         */
        fun trustPassenger(uuid: UUID, gameTime: Long, ticks: Int) {
            instance?.trustedUntil?.put(uuid, gameTime + ticks)
        }
    }

    init {
        instance = this
    }


    private val LOGGER = org.slf4j.LoggerFactory.getLogger("ShieldSolidBarrier")

    /** Cache for CuriosIntegration.hasMatchingCard() reflection calls. */
    private val cardCache = HashMap<UUID, Pair<Long, Boolean>>()  // UUID -> (expiry tick, result)

    private fun hasCardCached(entity: LivingEntity, code: String, gameTime: Long): Boolean {
        val cached = cardCache[entity.uuid]
        if (cached != null && cached.first > gameTime) return cached.second
        val result = CuriosIntegration.hasMatchingCard(entity, code)
        cardCache[entity.uuid] = Pair(gameTime + 20L, result)
        return result
    }

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val level = event.level
        if (level.isClientSide) return
        // Throttle: living entities move slowly (~0.26 blocks/tick sprint).
        // 3-tick interval → max 0.78 block penetration, compensated by pushBack.
        if (level.gameTime % 3L != 0L) return

        val manager = ShieldManager.getInstance()
        val cfg     = ShieldConfig.get().general
        val padding = cfg.shieldPadding

        // Periodic cleanup of expired trust entries and card cache
        if (level.gameTime % 60L == 0L) {
            trustedUntil.entries.removeIf { (_, expiry) -> expiry <= level.gameTime }
            cardCache.entries.removeIf { (_, pair) -> pair.first <= level.gameTime }
        }

        for ((shipId, shield) in manager.allShields) {
            if (!shield.isActive || shield.currentHP <= 0 || !shield.isSolidMode) {
                knownInside.remove(shipId)
                knownOutside.remove(shipId)
                initializedShields.remove(shipId)
                continue
            }

            val ownerPos = manager.getShieldOwnerPos(shipId) ?: continue
            val ship = level.getShipManagingPos(ownerPos) ?: continue
            val worldAABB = ship.worldAABB ?: continue

            val shieldAABB = AABB(
                worldAABB.minX() - padding, worldAABB.minY() - padding, worldAABB.minZ() - padding,
                worldAABB.maxX() + padding, worldAABB.maxY() + padding, worldAABB.maxZ() + padding
            )

            val inside  = knownInside.getOrPut(shipId)  { HashSet() }
            val outside = knownOutside.getOrPut(shipId) { HashSet() }

            // --- Initial scan: grandfather everyone already inside on first activation ---
            if (!initializedShields.contains(shipId)) {
                val current = level.getEntitiesOfClass(LivingEntity::class.java, shieldAABB)
                for (e in current) { if (e.isAlive) inside.add(e.uuid) }
                initializedShields.add(shipId)
            }

            // --- Living entity barrier ---
            val entities = level.getEntitiesOfClass(LivingEntity::class.java, shieldAABB)
            for (entity in entities) {
                if (!entity.isAlive) continue
                val uuid = entity.uuid
                val pos  = entity.position()
                val insideNow = shieldAABB.contains(pos.x, pos.y, pos.z)

                // Boarding pod passengers: trusted for a fixed duration after breach
                if (trustedUntil[uuid]?.let { it > level.gameTime } == true) {
                    inside.add(uuid)
                    continue
                }

                when {
                    inside.contains(uuid) -> {
                        if (!insideNow) {
                            inside.remove(uuid)
                            outside.add(uuid)
                        }
                    }
                    outside.contains(uuid) -> {
                        if (insideNow) {
                            if (hasCardCached(entity, shield.accessCode, level.gameTime)) {
                                inside.add(uuid)
                                outside.remove(uuid)
                            } else {
                                pushBack(entity, shieldAABB)
                            }
                        }
                    }
                    else -> {
                        // Untracked entity appeared inside — unauthorized entry (e.g. teleport).
                        // Check card; if none, push out immediately.
                        if (insideNow) {
                            if (hasCardCached(entity, shield.accessCode, level.gameTime)) {
                                inside.add(uuid)
                            } else {
                                outside.add(uuid)
                                pushBack(entity, shieldAABB)
                            }
                        }
                    }
                }
            }

            // --- Force-based ship repulsion (replaces VSDistanceJoint) ---
            try {
                val shieldAABBdc = AABBd(
                    worldAABB.minX() - padding, worldAABB.minY() - padding, worldAABB.minZ() - padding,
                    worldAABB.maxX() + padding, worldAABB.maxY() + padding, worldAABB.maxZ() + padding
                )

                val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)

                for (foreign: Ship in level.getShipsIntersecting(shieldAABBdc)) {
                    if (foreign.id == shipId) continue
                    if (PodShipManager.isTrustedPodShip(foreign.id)) continue
                    if (foreignShipHasMatchingCard(level as ServerLevel, foreign.id, shield.accessCode)) continue

                    val foreignAABB = foreign.worldAABB ?: continue

                    // SAT: overlap on each axis
                    val overlapX = min(shieldAABBdc.maxX, foreignAABB.maxX()) - max(shieldAABBdc.minX, foreignAABB.minX())
                    val overlapY = min(shieldAABBdc.maxY, foreignAABB.maxY()) - max(shieldAABBdc.minY, foreignAABB.minY())
                    val overlapZ = min(shieldAABBdc.maxZ, foreignAABB.maxZ()) - max(shieldAABBdc.minZ, foreignAABB.minZ())
                    if (overlapX <= 0 || overlapY <= 0 || overlapZ <= 0) continue

                    // Minimum penetration axis → push along shortest escape direction
                    val minOverlap = min(overlapX, min(overlapY, overlapZ))

                    val shieldCX = (shieldAABBdc.minX + shieldAABBdc.maxX) * 0.5
                    val shieldCY = (shieldAABBdc.minY + shieldAABBdc.maxY) * 0.5
                    val shieldCZ = (shieldAABBdc.minZ + shieldAABBdc.maxZ) * 0.5
                    val foreignCX = (foreignAABB.minX() + foreignAABB.maxX()) * 0.5
                    val foreignCY = (foreignAABB.minY() + foreignAABB.maxY()) * 0.5
                    val foreignCZ = (foreignAABB.minZ() + foreignAABB.maxZ()) * 0.5

                    var dx = 0.0; var dy = 0.0; var dz = 0.0
                    when (minOverlap) {
                        overlapX -> dx = if (foreignCX > shieldCX) 1.0 else -1.0
                        overlapY -> dy = if (foreignCY > shieldCY) 1.0 else -1.0
                        overlapZ -> dz = if (foreignCZ > shieldCZ) 1.0 else -1.0
                    }

                    // Quadratic: soft touch at edge, stiff wall deeper in
                    val forceMag = minOverlap * minOverlap * cfg.shipRepulsionForce

                    // null pos → force at CoM → ZERO torque → no tumbling
                    val pushForce = Vector3d(dx * forceMag, dy * forceMag, dz * forceMag)
                    gtpa.applyWorldForce(foreign.id, pushForce, null)
                    gtpa.applyWorldForce(shipId, Vector3d(-dx * forceMag, -dy * forceMag, -dz * forceMag), null)
                }
            } catch (e: Exception) {
                LOGGER.warn("[SolidBarrier] Repulsion failed for ship {}: {}", shipId, e.message)
            }
        }
    }

    /**
     * Returns true if the foreign ship's Shield Generator contains a FrequencyIDCard
     * with an access code matching [requiredCode].
     */
    private fun foreignShipHasMatchingCard(level: ServerLevel, foreignShipId: Long, requiredCode: String): Boolean {
        if (requiredCode.isBlank()) return false
        val generatorPos = ShieldManager.getInstance().getShieldOwnerPos(foreignShipId) ?: return false
        val be = level.getBlockEntity(generatorPos) as? ShieldGeneratorBlockEntity ?: return false
        val card = be.cardSlot.getItem(0)  // getCardSlot() — public getter in Java BE
        return FrequencyIDCardItem.hasMatchingCode(card, requiredCode)
    }

    private fun pushBack(entity: LivingEntity, aabb: AABB) {
        val pos = entity.position()
        val cx = (aabb.minX + aabb.maxX) * 0.5
        val cy = (aabb.minY + aabb.maxY) * 0.5
        val cz = (aabb.minZ + aabb.maxZ) * 0.5

        val clampedX = pos.x.coerceIn(aabb.minX, aabb.maxX)
        val clampedY = pos.y.coerceIn(aabb.minY, aabb.maxY)
        val clampedZ = pos.z.coerceIn(aabb.minZ, aabb.maxZ)

        var dx = clampedX - cx
        var dy = clampedY - cy
        var dz = clampedZ - cz
        val len = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (len > 1e-6) { dx /= len; dy /= len; dz /= len }
        else { dx = 0.0; dy = 0.0; dz = 1.0 }

        entity.teleportTo(clampedX + dx * 0.6, clampedY + dy * 0.6, clampedZ + dz * 0.6)
        entity.setDeltaMovement(dx * 0.5, dy * 0.5, dz * 0.5)
    }
}
