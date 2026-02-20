package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.client.VSShieldsModClient
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

class VSShieldsModForgeClient {
    companion object {
        @JvmStatic
        fun clientInit(event: FMLClientSetupEvent) {
            VSShieldsModClient.initClient()
        }
    }
}
