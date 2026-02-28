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


    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val level = event.level
        if (level.isClientSide) return

        val manager = ShieldManager.getInstance()
        val padding  = ShieldConfig.get().general.shieldPadding
        val repulse  = ShieldConfig.get().general.shipRepulsionForce

        // Periodic cleanup of expired trust entries
        if (level.gameTime % 20L == 0L) {
            trustedUntil.entries.removeIf { (_, expiry) -> expiry <= level.gameTime }
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
                            if (CuriosIntegration.hasMatchingCard(entity, shield.accessCode)) {
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
                            if (CuriosIntegration.hasMatchingCard(entity, shield.accessCode)) {
                                inside.add(uuid)
                            } else {
                                outside.add(uuid)
                                pushBack(entity, shieldAABB)
                            }
                        }
                    }
                }
            }

            // --- VS2 ship collision (velocity-based elastic impulse) ---
            // Reads actual velocities of both ships, computes elastic collision impulse,
            // applies it as a 1-tick corrective force at each ship's CoM (no torque).
            try {
                val shieldMat = ship.shipToWorld
                val shieldComX = shieldMat.m30()
                val shieldComY = shieldMat.m31()
                val shieldComZ = shieldMat.m32()
                val vA = ship.velocity

                val shieldAABBdc = AABBd(
                    worldAABB.minX() - padding, worldAABB.minY() - padding, worldAABB.minZ() - padding,
                    worldAABB.maxX() + padding, worldAABB.maxY() + padding, worldAABB.maxZ() + padding
                )

                val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)

                for (foreign: Ship in level.getShipsIntersecting(shieldAABBdc)) {
                    if (foreign.id == shipId) continue
                    // Boarding pod ships pass through the barrier (they breach via their own logic)
                    if (PodShipManager.isTrustedPodShip(foreign.id)) continue
                    if (foreignShipHasMatchingCard(level as ServerLevel, foreign.id, shield.accessCode)) continue

                    val foreignMat = foreign.shipToWorld
                    val fComX = foreignMat.m30()
                    val fComY = foreignMat.m31()
                    val fComZ = foreignMat.m32()

                    // Unit normal from shield CoM toward foreign CoM
                    var nx = fComX - shieldComX
                    var ny = fComY - shieldComY
                    var nz = fComZ - shieldComZ
                    val dist = Math.sqrt(nx * nx + ny * ny + nz * nz)
                    if (dist < 1e-6) continue
                    nx /= dist; ny /= dist; nz /= dist

                    // Relative velocity along normal: positive = ships approaching each other
                    val vB = foreign.velocity
                    val vRelN = (vA.x() - vB.x()) * nx +
                                (vA.y() - vB.y()) * ny +
                                (vA.z() - vB.z()) * nz
                    if (vRelN <= 0.05) continue  // already separating or nearly stationary

                    // Scale force by approach speed: faster closing = harder bounce.
                    // shipRepulsionForce acts as "stiffness" — tune in config.
                    val f = vRelN * repulse

                    // Apply at each ship's CoM — no torque introduced
                    gtpa.applyWorldForce(shipId,     Vector3d(-nx * f, -ny * f, -nz * f),
                                                     Vector3d(shieldComX, shieldComY, shieldComZ))
                    gtpa.applyWorldForce(foreign.id, Vector3d( nx * f,  ny * f,  nz * f),
                                                     Vector3d(fComX, fComY, fComZ))
                }
            } catch (_: Exception) {
                // VS2 physics world unavailable this tick — skip silently
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
