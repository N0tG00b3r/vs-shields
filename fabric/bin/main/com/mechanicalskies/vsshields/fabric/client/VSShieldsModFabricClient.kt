package com.mechanicalskies.vsshields.fabric.client

import com.mechanicalskies.vsshields.client.VSShieldsModClient
import net.fabricmc.api.ClientModInitializer

class VSShieldsModFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        VSShieldsModClient.initClient()
    }
}
