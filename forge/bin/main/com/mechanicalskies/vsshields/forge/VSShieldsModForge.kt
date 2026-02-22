package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.VSShieldsMod
import com.mechanicalskies.vsshields.blockentity.CloakingFieldGeneratorBlockEntity
import com.mechanicalskies.vsshields.blockentity.ShieldBatteryInputBlockEntity
import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity
import com.mechanicalskies.vsshields.network.ModNetwork
import com.mechanicalskies.vsshields.shield.CloakManager
import com.mechanicalskies.vsshields.shield.ShieldManager
import dev.architectury.platform.forge.EventBuses
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLPaths

@Mod(VSShieldsMod.MOD_ID)
class VSShieldsModForge {
    init {
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus
        EventBuses.registerModEventBus(VSShieldsMod.MOD_ID, modEventBus)

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) { ClientProxy() }

        VSShieldsMod.loadConfig(FMLPaths.GAMEDIR.get())
        VSShieldsMod.init()

        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(ShieldDamageHandler())
        MinecraftForge.EVENT_BUS.register(ShieldBarrierHandler())
        MinecraftForge.EVENT_BUS.register(ShieldEnergyCapability())
        MinecraftForge.EVENT_BUS.register(BatteryInputEnergyCapability())
        MinecraftForge.EVENT_BUS.register(CloakEnergyCapability())
        MinecraftForge.EVENT_BUS.register(ShieldJammerEnergyCapability())

        ShieldGeneratorBlockEntity.setEnergyInputHook { level, pos, be ->
            CreateCompat.tickKineticInput(level, pos, be)
        }

        ShieldBatteryInputBlockEntity.setEnergyInputHook { level, pos, be ->
            CreateCompat.tickBatteryKineticInput(level, pos, be)
        }

        CloakingFieldGeneratorBlockEntity.setEnergyInputHook { level, pos, be ->
            CreateCompat.tickCloakKineticInput(level, pos, be)
        }

        com.mechanicalskies.vsshields.blockentity.ShieldJammerInputBlockEntity.setEnergyInputHook { level, pos, be ->
            CreateCompat.tickShieldJammerInput(level, pos, be)
        }
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        ShieldManager.getInstance().setServer(event.server)
        CloakManager.getInstance().setServer(event.server)
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            ShieldManager.getInstance().tick()
        }
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        ShieldManager.getInstance().clear()
        CloakManager.getInstance().clear()
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        ModNetwork.sendSyncToPlayer(player)
        CloakManager.getInstance().sendAllCloakedShipsToClient(player)
    }
}
