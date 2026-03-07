package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos

/**
 * Forge event handler: suppresses rendering of LivingEntity on cloaked ships.
 * Non-living entities are handled by MixinEntityRendererCloak.
 */
object CloakEntityRenderHandler {

    @SubscribeEvent
    fun onRenderLiving(event: RenderLivingEvent.Pre<*, *>) {
        val registry = CloakedShipsRegistry.getInstance()
        if (!registry.hasAnyCloakedShips()) return

        val entity = event.entity
        val ship = entity.level().getShipManagingPos(entity.blockPosition()) ?: return
        if (!registry.isCloaked(ship.id)) return

        val player = Minecraft.getInstance().player ?: return
        if (isPlayerAboard(ship, player)) return

        event.isCanceled = true
    }

    private fun isPlayerAboard(ship: Ship, player: Player): Boolean {
        return try {
            val aabb: AABBdc = ship.worldAABB
            val t = 2.0
            player.x >= aabb.minX() - t && player.x <= aabb.maxX() + t &&
                    player.y >= aabb.minY() - t && player.y <= aabb.maxY() + t &&
                    player.z >= aabb.minZ() - t && player.z <= aabb.maxZ() + t
        } catch (e: Exception) {
            false
        }
    }
}
