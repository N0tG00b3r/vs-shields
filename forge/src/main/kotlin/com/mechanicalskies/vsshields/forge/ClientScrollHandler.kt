package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.item.GravitationalMineLauncherItem
import com.mechanicalskies.vsshields.network.ModNetwork
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

class ClientScrollHandler {
    @SubscribeEvent
    fun onScroll(event: InputEvent.MouseScrollingEvent) {
        val player = Minecraft.getInstance().player ?: return
        val held = player.mainHandItem.takeIf { it.item is GravitationalMineLauncherItem }
            ?: player.offhandItem.takeIf { it.item is GravitationalMineLauncherItem }
            ?: return
        if (!Screen.hasShiftDown()) return
        ModNetwork.sendMineScroll(if (event.scrollDelta > 0.0) 1 else -1)
        event.isCanceled = true
    }
}
