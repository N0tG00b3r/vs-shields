package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mechanicalskies.vsshields.network.ModNetwork
import com.mechanicalskies.vsshields.shield.ShieldInstance
import com.mechanicalskies.vsshields.shield.ShieldManager
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.event.entity.ProjectileImpactEvent
import net.minecraftforge.event.level.ExplosionEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos

class ShieldDamageHandler {

    companion object {
        private fun getProjectileDamage(entity: Entity): Double {
            val cfg = ShieldConfig.get().damage

            val registryKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.type)
            val registryName = registryKey?.toString() ?: ""
            val namespace = registryKey?.namespace ?: ""
            val path = registryKey?.path ?: ""

            cfg.projectiles[registryName]?.let { return it }

            if (namespace == "createbigcannons" || namespace == "cbc") {
                return when {
                    path.contains("he") || path.contains("explosive") -> cfg.cbcHE
                    path.contains("ap") || path.contains("armor_piercing") -> cfg.cbcAP
                    else -> cfg.cbcSolidShot
                }
            }

            for ((pattern, damage) in cfg.projectileClassPatterns) {
                if (path.contains(pattern)) return damage
            }

            val className = entity.javaClass.name.lowercase()

            if (className.contains("nukateam") || className.contains(".cgs.")) {
                for ((pattern, damage) in cfg.projectileClassPatterns) {
                    if (className.contains(pattern)) return damage
                }
                return cfg.moddedProjectileDefault
            }

            if (className.contains("bigcannon") || className.contains("cannonball")) {
                return when {
                    className.contains("he") || className.contains("explosive") -> cfg.cbcHE
                    className.contains("ap") || className.contains("armor_piercing") -> cfg.cbcAP
                    else -> cfg.cbcSolidShot
                }
            }

            if (namespace.isNotEmpty() && namespace != "minecraft") {
                return cfg.moddedProjectileDefault
            }

            return cfg.unknownProjectileDefault
        }

        /**
         * Fallback: find a shielded ship whose world-AABB (inflated by shieldPadding)
         * contains the given world-space position.
         * Used when getShipManagingPos returns null (hit is near but not on a ship block).
         */
        private fun findShieldedShipNearPos(level: Level, pos: Vec3): Pair<Ship, ShieldInstance>? {
            val manager = ShieldManager.getInstance()
            val padding = ShieldConfig.get().general.shieldPadding

            for ((shipId, shield) in manager.allShields) {
                if (!shield.isActive || shield.currentHP <= 0) continue

                val ownerPos = manager.getShieldOwnerPos(shipId) ?: continue
                val ship = level.getShipManagingPos(ownerPos) ?: continue

                val aabb = ship.worldAABB ?: continue
                val inflated = AABB(
                    aabb.minX() - padding, aabb.minY() - padding, aabb.minZ() - padding,
                    aabb.maxX() + padding, aabb.maxY() + padding, aabb.maxZ() + padding
                )

                if (inflated.contains(pos.x, pos.y, pos.z)) {
                    return Pair(ship, shield)
                }
            }
            return null
        }

        /** Public accessor for ShieldBarrierHandler */
        fun getProjectileDamagePublic(entity: Entity): Double = getProjectileDamage(entity)
    }

    @SubscribeEvent
    fun onExplosionStart(event: ExplosionEvent.Start) {
        val level = event.level
        if (level.isClientSide) return

        val explosion = event.explosion
        val explosionPos = explosion.position
        val manager = ShieldManager.getInstance()

        // Fast path: explosion directly on a ship block
        var shield: ShieldInstance? = null
        val ship = level.getShipManagingPos(BlockPos.containing(explosionPos))
        if (ship != null) {
            shield = manager.getShield(ship.id)
        }

        // Slow path: explosion is near a shielded ship (within padding)
        if (shield == null) {
            val result = findShieldedShipNearPos(level, explosionPos) ?: return
            shield = result.second
        }

        if (shield.isActive && shield.currentHP > 0) {
            val power = explosion.radius.toDouble()
            val explosionDamage = power * power * ShieldConfig.get().damage.explosionPowerFactor
            shield.damage(explosionDamage, manager.currentTick)

            val server = level.server
            if (server != null) {
                ModNetwork.sendShieldHit(server, shield.shipId,
                    explosionPos.x, explosionPos.y, explosionPos.z,
                    explosionDamage.toFloat())
                if (shield.currentHP <= 0) {
                    ModNetwork.sendShieldBreak(server, shield.shipId)
                }
            }

            event.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onProjectileImpact(event: ProjectileImpactEvent) {
        val projectile = event.entity
        val level = projectile.level()
        if (level.isClientSide) return

        val hitPos = event.rayTraceResult.location
        val manager = ShieldManager.getInstance()

        // Fast path: projectile hit directly on a ship block
        var shield: ShieldInstance? = null
        val ship = level.getShipManagingPos(BlockPos.containing(hitPos))
        if (ship != null) {
            shield = manager.getShield(ship.id)
        }

        // Slow path: projectile hit near a shielded ship (within padding)
        if (shield == null) {
            val result = findShieldedShipNearPos(level, hitPos) ?: return
            shield = result.second
        }

        if (shield.isActive && shield.currentHP > 0) {
            val damage = getProjectileDamage(projectile)
            if (damage <= 0.0) return

            shield.damage(damage, manager.currentTick)

            val server = level.server
            if (server != null) {
                ModNetwork.sendShieldHit(server, shield.shipId,
                    hitPos.x, hitPos.y, hitPos.z,
                    damage.toFloat())
                if (shield.currentHP <= 0) {
                    ModNetwork.sendShieldBreak(server, shield.shipId)
                }
            }

            event.isCanceled = true
        }
    }

    /**
     * Intercept Alex's Caves nuclear entities (nuclear_explosion / nuclear_bomb).
     * These bypass standard ExplosionEvent — they spawn as entities that deal damage
     * via their own tick logic. We catch them at spawn and destroy them if they're
     * inside a shielded ship's inflated AABB.
     */
    @SubscribeEvent
    fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        val level = event.level
        if (level.isClientSide) return

        val entity = event.entity
        val registryKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.type) ?: return
        val registryName = registryKey.toString()

        // Only handle Alex's Caves nuclear entities
        if (registryName != "alexscaves:nuclear_explosion" &&
            registryName != "alexscaves:nuclear_bomb") return

        val entityPos = Vec3(entity.x, entity.y, entity.z)
        val manager = ShieldManager.getInstance()

        // Check if on a ship block first (fast path)
        var shield: ShieldInstance? = null
        val ship = level.getShipManagingPos(BlockPos.containing(entityPos))
        if (ship != null) {
            shield = manager.getShield(ship.id)
        }

        // Slow path: check inflated AABB
        if (shield == null) {
            val result = findShieldedShipNearPos(level, entityPos) ?: return
            shield = result.second
        }

        if (shield.isActive && shield.currentHP > 0) {
            val nukeDamage = ShieldConfig.get().damage.alexsCavesNukeDamage
            shield.damage(nukeDamage, manager.currentTick)

            // Send visual effects to clients
            val server = level.server
            if (server != null) {
                ModNetwork.sendShieldHit(server, shield.shipId,
                    entity.x, entity.y, entity.z,
                    nukeDamage.toFloat())
                if (shield.currentHP <= 0) {
                    ModNetwork.sendShieldBreak(server, shield.shipId)
                }
                ModNetwork.sendNukeVisual(server, entity.x, entity.y, entity.z)
            }

            // Remove the nuke entity — prevent it from dealing damage
            entity.discard()
            event.isCanceled = true
        }
    }
}
