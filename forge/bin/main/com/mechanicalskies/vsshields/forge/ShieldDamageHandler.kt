package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mechanicalskies.vsshields.network.ModNetwork
import com.mechanicalskies.vsshields.shield.ShieldInstance
import com.mechanicalskies.vsshields.shield.ShieldManager
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.event.entity.ProjectileImpactEvent
import net.minecraftforge.event.level.ExplosionEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.ForgeRegistries
import org.slf4j.LoggerFactory
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos

class ShieldDamageHandler {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("vs_shields")

        private fun getProjectileDamage(entity: Entity): Double {
            val cfg = ShieldConfig.get().damage

            val registryKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.type)
            val registryName = registryKey?.toString() ?: ""
            val namespace = registryKey?.namespace ?: ""
            val path = registryKey?.path ?: ""

            // Exact registry name match (highest priority — includes CGS entries from config)
            cfg.projectiles[registryName]?.let { return it }

            // Create Big Cannons — main mod
            if (namespace == "createbigcannons" || namespace == "cbc") {
                return when {
                    path.contains("he") || path.contains("explosive") -> cfg.cbcHE
                    path.contains("ap") || path.contains("armor_piercing") -> cfg.cbcAP
                    // Autocannon / machine gun fires small rapid rounds — much less damage per shot
                    path.contains("autocannon") || path.contains("auto_cannon") || path.contains("machine_gun") -> cfg.cbcAutocannon
                    else -> cfg.cbcSolidShot
                }
            }

            // Create Big Cannons — Nukes addon
            // NukeShellProjectile extends FuzedBigCannonProjectile (NOT Minecraft Projectile),
            // so it is caught by ShieldBarrierHandler via isCbcEntity(). This block covers
            // any future cbc_nukes projectiles added to ShieldDamageHandler directly.
            if (namespace == "cbc_nukes") {
                return cfg.projectiles["$namespace:$path"] ?: when {
                    path.contains("nuke") -> cfg.alexsCavesNukeDamage
                    else -> cfg.moddedProjectileDefault
                }
            }

            // Create Gunsmithing projectile entities — per-weapon damage via path pattern
            // (Hitscan weapons are handled separately by tryRegisterCgsHitscanHandler)
            if (namespace == "cgs" || namespace == "ntgl") {
                return when {
                    path.contains("rocket") || path.contains("missile") ->
                        cfg.projectileClassPatterns["rocket"] ?: 40.0
                    path.contains("spear")      -> cfg.projectileClassPatterns["spear"]      ?: 20.0
                    path.contains("nail_steel") -> 8.0
                    path.contains("nail")       -> 6.0
                    path.contains("incendiary") -> 12.0
                    path.contains("blaze") || path.contains("fire") -> 8.0
                    else -> cfg.moddedProjectileDefault
                }
            }

            // Generic path pattern check
            for ((pattern, damage) in cfg.projectileClassPatterns) {
                if (path.contains(pattern)) return damage
            }

            val className = entity.javaClass.name.lowercase()

            // NTGL/CGS class name fallback (entities not in cgs: namespace)
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

        // ─────────────────────────────────────────────────────────────────────
        // CGS Hitscan interception (Option C)
        //
        // Subscribes to GunFireEvent$Pre via reflection so NTGL is not a
        // required compile-time dependency. Safe no-op if CGS/NTGL is absent.
        //
        // When a hitscan gun fires, we raycast from the shooter's eye along the
        // look vector and check all active shield AABBs. If a shield is hit from
        // outside (shooter is NOT inside the bubble), we:
        //   1. Deal per-weapon shield damage
        //   2. Cancel GunFireEvent$Pre — prevents the shot from passing through
        //
        // Projectile-entity weapons (nails, rockets, etc.) are already handled
        // by onProjectileImpact / the projectiles config map — not this handler.
        // ─────────────────────────────────────────────────────────────────────

        // Try both NTGL and CGS package locations for robustness
        private val CGS_FIRE_EVENT_CLASSES = listOf(
            "com.nukateam.ntgl.common.event.GunFireEvent\$Pre",
            "com.nukateam.cgs.common.event.GunFireEvent\$Pre"
        )

        /**
         * Call once during mod init. If NTGL is loaded, registers the hitscan
         * handler on the provided Forge event bus. Silent no-op otherwise.
         */
        fun tryRegisterCgsHitscanHandler(eventBus: IEventBus) {
            if (!ShieldConfig.get().cgs.enableHitscan) return

            for (className in CGS_FIRE_EVENT_CLASSES) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val eventClass = Class.forName(className) as Class<Event>
                    eventBus.addListener(EventPriority.HIGH, true, eventClass, ::handleCgsGunFire)
                    LOGGER.info("[VS Shields] CGS hitscan handler registered via {}", className)
                    return
                } catch (_: ClassNotFoundException) {
                    // Try next class name
                } catch (e: Exception) {
                    LOGGER.warn("[VS Shields] Failed to register CGS hitscan handler via {}: {}", className, e.message)
                }
            }
            // CGS/NTGL not installed — silently skip
        }

        /**
         * Walk the class hierarchy of [obj] to find a no-arg method whose name
         * is one of [names], invoke it, and return the result.
         */
        private fun invokeMethod(obj: Any, vararg names: String): Any? {
            var cls: Class<*>? = obj.javaClass
            while (cls != null) {
                for (name in names) {
                    try {
                        val m = cls.getDeclaredMethod(name)
                        m.isAccessible = true
                        return m.invoke(obj)
                    } catch (_: NoSuchMethodException) { }
                }
                cls = cls.superclass
            }
            return null
        }

        /** Called by the reflected GunFireEvent$Pre listener. */
        private fun handleCgsGunFire(event: Event) {
            // Extract shooter via NTGL's getEntity() accessor
            val shooter = invokeMethod(event, "getEntity", "getShooter", "getPlayer")
                    as? LivingEntity ?: return
            val level = shooter.level()
            if (level.isClientSide) return

            // Determine per-shot damage from weapon item registry name
            val stack = invokeMethod(event, "getItemStack", "getStack", "getGun", "getWeapon")
                    as? ItemStack
            val weaponPath = stack?.let {
                ForgeRegistries.ITEMS.getKey(it.item)?.path ?: ""
            } ?: ""

            val cgsCfg = ShieldConfig.get().cgs
            val dmg: Double = when {
                weaponPath.contains("gatling")   -> cgsCfg.gatlingBullet
                weaponPath.contains("revolver")  -> cgsCfg.revolverShot
                weaponPath.contains("flintlock") -> cgsCfg.flintlockBall
                weaponPath.contains("shotgun")   -> cgsCfg.shotgunBurst
                else -> return  // not a known hitscan weapon — skip
            }

            // Raycast from shooter eye along look vector (max 200 blocks)
            val eyePos = shooter.getEyePosition()
            val rayEnd = eyePos.add(shooter.lookAngle.scale(200.0))

            val manager = ShieldManager.getInstance()
            val padding = ShieldConfig.get().general.shieldPadding

            for ((_, shield) in manager.allShields) {
                if (!shield.isActive || shield.currentHP <= 0) continue

                val ownerPos = manager.getShieldOwnerPos(shield.shipId) ?: continue
                val ship = level.getShipManagingPos(ownerPos) ?: continue
                val aabb = ship.worldAABB ?: continue

                val inflated = AABB(
                    aabb.minX() - padding, aabb.minY() - padding, aabb.minZ() - padding,
                    aabb.maxX() + padding, aabb.maxY() + padding, aabb.maxZ() + padding
                )

                // clip() returns empty when eyePos is INSIDE the AABB, so
                // friendly-fire (shooting from inside the shield) is automatically allowed.
                val hitOpt = inflated.clip(eyePos, rayEnd)
                if (hitOpt.isEmpty) continue

                val hitPoint = hitOpt.get()
                shield.damage(dmg, manager.currentTick)

                val server = level.server
                if (server != null) {
                    ModNetwork.sendShieldHit(server, shield.shipId,
                        hitPoint.x, hitPoint.y, hitPoint.z, dmg.toFloat())
                    if (shield.currentHP <= 0) {
                        ModNetwork.sendShieldBreak(server, shield.shipId)
                    }
                }

                // Cancel the fire event — shot is absorbed by the shield
                try { event.setCanceled(true) } catch (_: Exception) { }
                break  // stop at the first shield in the ray path
            }
        }
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
