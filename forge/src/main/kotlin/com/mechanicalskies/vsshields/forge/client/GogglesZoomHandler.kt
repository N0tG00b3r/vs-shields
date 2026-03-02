package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.forge.CuriosIntegration
import com.mechanicalskies.vsshields.item.TacticalGogglesItem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.EquipmentSlot
import net.minecraftforge.client.event.ViewportEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/**
 * Handles Ctrl-hold zoom for Tactical Goggles (helmet slot or Curios head slot).
 * Works like OptiFine zoom — hold Ctrl to zoom 4×.
 */
object GogglesZoomHandler {

    @SubscribeEvent
    fun onComputeFov(event: ViewportEvent.ComputeFov) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.screen != null) return
        if (!Screen.hasControlDown()) return
        val helmet = player.getItemBySlot(EquipmentSlot.HEAD)
        val wearingInArmor = !helmet.isEmpty && helmet.item is TacticalGogglesItem
        val wearingInCurios = CuriosIntegration.hasGogglesInHeadSlot(player)
        if (!wearingInArmor && !wearingInCurios) return
        event.fov = event.fov * 0.25 // 4× zoom
    }
}
