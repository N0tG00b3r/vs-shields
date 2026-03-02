package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mechanicalskies.vsshields.network.ModNetwork
import com.mechanicalskies.vsshields.shield.ShieldInstance
import com.mechanicalskies.vsshields.shield.ShieldManager
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos

/**
 * Server-side tick handler that intercepts projectiles at the shield boundary.
 *
 * Each tick, for each active shield zone, scans projectile entities within
 * the shield's inflated AABB. Detects boundary crossing by comparing the
 * projectile's previous position (xOld/yOld/zOld) against the current position.
 *
 * Two-layer protection:
 * 1. External barrier: projectiles entering the shield zone from outside
 * 2. Friendly fire: projectiles inside the shield entering the ship's core AABB
 *
 * CBC shells (Create Big Cannons) and CGS/NTGL projectiles (nail, blaze_ball, etc.) do NOT
 * extend net.minecraft.world.entity.projectile.Projectile — so ProjectileImpactEvent never
 * fires for them. They are detected via isCbcEntity() which checks the entity namespace.
 */
class ShieldBarrierHandler {

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val level = event.level
        if (level.isClientSide) return

        val manager = ShieldManager.getInstance()
        val padding = ShieldConfig.get().general.shieldPadding
        val server = level.server ?: return

        for ((shipId, shield) in manager.allShields) {
            if (!shield.isActive || shield.currentHP <= 0) continue

            val ownerPos = manager.getShieldOwnerPos(shipId) ?: continue
            val ship: Ship = level.getShipManagingPos(ownerPos) ?: continue
            val worldAABB = ship.worldAABB ?: continue

            val coreAABB = AABB(
                worldAABB.minX(), worldAABB.minY(), worldAABB.minZ(),
                worldAABB.maxX(), worldAABB.maxY(), worldAABB.maxZ()
            )
            val shieldAABB = AABB(
                worldAABB.minX() - padding, worldAABB.minY() - padding, worldAABB.minZ() - padding,
                worldAABB.maxX() + padding, worldAABB.maxY() + padding, worldAABB.maxZ() + padding
            )

            // Catch standard Minecraft projectiles, CBC cannon shells,
            // AND CGS/NTGL projectiles (none of which extend vanilla Projectile)
            val entities = level.getEntities(null, shieldAABB) { entity ->
                entity is Projectile || isCbcEntity(entity)
            }
            for (entity in entities) {
                if (!entity.isAlive) continue

                val curPos  = Vec3(entity.x,    entity.y,    entity.z)
                val prevPos = Vec3(entity.xOld, entity.yOld, entity.zOld)

                // === OUTGOING CREW SHOT — skip if owner is inside the shield zone ===
                // Handles arrows, snowballs, and any standard MC Projectile fired from crew.
                if (entity is Projectile) {
                    val owner = entity.owner
                    if (owner != null && shieldAABB.contains(owner.x, owner.y, owner.z)) continue
                }

                val curInShield  = shieldAABB.contains(curPos.x,  curPos.y,  curPos.z)
                val prevInShield = shieldAABB.contains(prevPos.x, prevPos.y, prevPos.z)

                if (curInShield && !prevInShield) {
                    // === EXTERNAL BARRIER: entity just crossed the shield boundary ===
                    //
                    // Sanity-check: reject entities whose previous position is suspiciously far
                    // from the ship. This catches mods (e.g., CBC) that use setPos() instead of
                    // moveTo() when spawning projectiles — leaving entity.xOld at the Entity
                    // constructor default (0, 0, 0). For ships not near world origin, that default
                    // is hundreds of blocks away and would falsely trigger the external barrier
                    // against crew-fired shells.
                    //
                    // However, some modded projectiles (e.g., NTGL nailgun) also have uninitialized
                    // xOld but are legitimate INCOMING projectiles. To distinguish:
                    //  - CBC shell spawned at cannon muzzle → curPos is inside the core AABB → skip
                    //  - External projectile entering shield → curPos is in shield zone, NOT in core → intercept
                    if (!shieldAABB.inflate(200.0).contains(prevPos.x, prevPos.y, prevPos.z)) {
                        if (coreAABB.contains(curPos.x, curPos.y, curPos.z)) continue
                    }

                    val interceptPos = computeInterceptPoint(prevPos, curPos, shieldAABB)
                    interceptEntity(entity, shield, manager, server, shipId, interceptPos)
                    continue
                }

                if (curInShield && prevInShield) {
                    // Already inside shield zone — check friendly fire
                    val curInCore  = coreAABB.contains(curPos.x,  curPos.y,  curPos.z)
                    val prevInCore = coreAABB.contains(prevPos.x, prevPos.y, prevPos.z)

                    if (curInCore && !prevInCore) {
                        // === FRIENDLY FIRE: entity entering the ship's block zone ===
                        val interceptPos = computeInterceptPoint(prevPos, curPos, coreAABB)
                        interceptEntity(entity, shield, manager, server, shipId, interceptPos)
                    }
                }
            }
        }
    }

    /**
     * Returns true for modded projectile entities that are NOT subclasses of
     * net.minecraft.world.entity.projectile.Projectile but must still be blocked by the shield.
     *
     * Covers:
     *  - CBC cannon shells (createbigcannons, cbc, cbc_nukes) — extend FuzedBigCannonProjectile
     *  - CGS / NTGL projectiles (cgs, ntgl) — nail, blaze_ball, etc. don't extend vanilla Projectile
     *
     * Excludes living entities and item entities.
     */
    private fun isCbcEntity(entity: Entity): Boolean {
        if (entity is LivingEntity) return false
        if (entity is ItemEntity)   return false
        val namespace = ForgeRegistries.ENTITY_TYPES.getKey(entity.type)?.namespace ?: return false
        return namespace == "createbigcannons" || namespace == "cbc" || namespace == "cbc_nukes"
            || namespace == "cgs" || namespace == "ntgl"
    }

    private fun interceptEntity(
        entity: Entity,
        shield: ShieldInstance,
        manager: ShieldManager,
        server: net.minecraft.server.MinecraftServer,
        shipId: Long,
        interceptPos: Vec3
    ) {
        val damage = ShieldDamageHandler.getProjectileDamagePublic(entity)
        if (damage <= 0.0) return

        shield.damage(damage, manager.currentTick)

        ModNetwork.sendShieldHit(
            server, shipId,
            interceptPos.x, interceptPos.y, interceptPos.z,
            damage.toFloat()
        )
        if (shield.currentHP <= 0) {
            ModNetwork.sendShieldBreak(server, shipId)
        }

        // CBC Nuke shells: send nuclear explosion visual at the intercept point,
        // matching the effect that fires when alexscaves:nuclear_explosion is blocked.
        val cbcNs = ForgeRegistries.ENTITY_TYPES.getKey(entity.type)?.namespace
        if (cbcNs == "cbc_nukes") {
            ModNetwork.sendNukeVisual(server, interceptPos.x, interceptPos.y, interceptPos.z)
        }

        entity.discard()
    }

    /**
     * Compute the point where the line segment [from -> to] enters the AABB.
     * If 'from' is already inside, returns 'to' (fallback).
     * This gives us the exact shield-surface position for the hit effect.
     */
    private fun computeInterceptPoint(from: Vec3, to: Vec3, aabb: AABB): Vec3 {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z

        var tMin = 0.0
        var tMax = 1.0

        // Check each axis pair
        tMin = axisIntersect(from.x, dx, aabb.minX, aabb.maxX, tMin, tMax).first
        tMax = axisIntersect(from.x, dx, aabb.minX, aabb.maxX, tMin, tMax).second
        if (tMin > tMax) return to

        tMin = axisIntersect(from.y, dy, aabb.minY, aabb.maxY, tMin, tMax).first
        tMax = axisIntersect(from.y, dy, aabb.minY, aabb.maxY, tMin, tMax).second
        if (tMin > tMax) return to

        tMin = axisIntersect(from.z, dz, aabb.minZ, aabb.maxZ, tMin, tMax).first
        tMax = axisIntersect(from.z, dz, aabb.minZ, aabb.maxZ, tMin, tMax).second
        if (tMin > tMax) return to

        // tMin is the entry parameter
        val t = tMin.coerceIn(0.0, 1.0)
        return Vec3(from.x + dx * t, from.y + dy * t, from.z + dz * t)
    }

    private fun axisIntersect(
        origin: Double, dir: Double,
        min: Double, max: Double,
        currentTMin: Double, currentTMax: Double
    ): Pair<Double, Double> {
        if (Math.abs(dir) < 1e-10) {
            return if (origin < min || origin > max) {
                Pair(Double.MAX_VALUE, Double.MIN_VALUE) // no intersection
            } else {
                Pair(currentTMin, currentTMax)
            }
        }
        var t1 = (min - origin) / dir
        var t2 = (max - origin) / dir
        if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
        return Pair(
            maxOf(currentTMin, t1),
            minOf(currentTMax, t2)
        )
    }
}
