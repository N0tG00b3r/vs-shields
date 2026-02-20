package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mechanicalskies.vsshields.shield.ShieldManager
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Explosion
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.entity.ProjectileImpactEvent
import net.minecraftforge.event.level.ExplosionEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.ForgeRegistries
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
    }

    @SubscribeEvent
    fun onExplosionStart(event: ExplosionEvent.Start) {
        val level = event.level
        if (level.isClientSide) return

        val explosion = event.explosion
        val explosionPos = explosion.position
        val manager = ShieldManager.getInstance()

        val ship = level.getShipManagingPos(BlockPos.containing(explosionPos)) ?: return
        val shield = manager.getShield(ship.id) ?: return

        if (shield.isActive && shield.currentHP > 0) {
            val power = explosion.radius.toDouble()
            val explosionDamage = power * power * ShieldConfig.get().damage.explosionPowerFactor
            shield.damage(explosionDamage, manager.currentTick)
            
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

        val ship = level.getShipManagingPos(BlockPos.containing(hitPos)) ?: return
        val shield = manager.getShield(ship.id) ?: return

        if (shield.isActive && shield.currentHP > 0) {
            val damage = getProjectileDamage(projectile)
            if (damage <= 0.0) return

            shield.damage(damage, manager.currentTick)
            event.isCanceled = true
        }
    }
}
