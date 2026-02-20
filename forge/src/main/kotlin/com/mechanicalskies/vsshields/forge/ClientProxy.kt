package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.forge.client.VSShieldsModForgeClient
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

class ClientProxy : Runnable {
    override fun run() {
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus
        modEventBus.addListener(VSShieldsModForgeClient::clientInit)
    }
}
