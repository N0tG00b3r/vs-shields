package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.forge.client.GogglesZoomHandler
import com.mechanicalskies.vsshields.forge.client.VSShieldsModForgeClient
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

class ClientProxy : Runnable {
    override fun run() {
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus
        modEventBus.addListener(VSShieldsModForgeClient::clientInit)
        modEventBus.addListener(VSShieldsModForgeClient::registerKeyMappings)
        modEventBus.addListener(VSShieldsModForgeClient::registerLayers)
        modEventBus.addListener(VSShieldsModForgeClient::addLayers)
        modEventBus.addListener(VSShieldsModForgeClient::registerRenderers)
        MinecraftForge.EVENT_BUS.register(ClientScrollHandler())
        MinecraftForge.EVENT_BUS.register(GogglesZoomHandler)
    }
}
