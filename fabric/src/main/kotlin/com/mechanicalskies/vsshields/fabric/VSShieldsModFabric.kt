package com.mechanicalskies.vsshields.fabric

import com.mechanicalskies.vsshields.VSShieldsMod
import com.mechanicalskies.vsshields.shield.ShieldManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader

class VSShieldsModFabric : ModInitializer {
    override fun onInitialize() {
        VSShieldsMod.loadConfig(FabricLoader.getInstance().gameDir)
        VSShieldsMod.init()

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            ShieldManager.getInstance().setServer(server)
        }

        ServerTickEvents.END_SERVER_TICK.register { _ ->
            ShieldManager.getInstance().tick()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            ShieldManager.getInstance().clear()
        }
    }
}
