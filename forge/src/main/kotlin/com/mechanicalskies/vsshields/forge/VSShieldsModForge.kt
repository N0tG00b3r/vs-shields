package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.VSShieldsMod
import com.mechanicalskies.vsshields.blockentity.CloakingFieldGeneratorBlockEntity
import com.mechanicalskies.vsshields.blockentity.ShieldBatteryInputBlockEntity
import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity
import com.mechanicalskies.vsshields.blockentity.SolidProjectionModuleBlockEntity
import com.mechanicalskies.vsshields.entity.BoardingPodEntity
import com.mechanicalskies.vsshields.entity.CockpitSeatEntity
import com.mechanicalskies.vsshields.entity.GravitationalMineEntity
import com.mechanicalskies.vsshields.shield.SolidModuleRegistry
import com.mechanicalskies.vsshields.network.ModNetwork
import com.mechanicalskies.vsshields.scanner.AnalyzerBlockCache
import com.mechanicalskies.vsshields.scanner.AnalyzerScanHandler
import com.mechanicalskies.vsshields.shield.CloakManager
import com.mechanicalskies.vsshields.shield.GravityFieldRegistry
import com.mechanicalskies.vsshields.shield.ShieldManager
import dev.architectury.platform.forge.EventBuses
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3d
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLPaths
import org.valkyrienskies.mod.common.getShipManagingPos

@Mod(VSShieldsMod.MOD_ID)
class VSShieldsModForge {
    init {
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus
        EventBuses.registerModEventBus(VSShieldsMod.MOD_ID, modEventBus)

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) { ClientProxy() }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable { MinecraftForge.EVENT_BUS.register(AnalyzerOutlineRenderer()) }
        }

        VSShieldsMod.loadConfig(FMLPaths.GAMEDIR.get())
        VSShieldsMod.init()

        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(ShieldDamageHandler())
        MinecraftForge.EVENT_BUS.register(ShieldBarrierHandler())
        ShieldDamageHandler.tryRegisterCgsHitscanHandler(MinecraftForge.EVENT_BUS)
        MinecraftForge.EVENT_BUS.register(ShieldEnergyCapability())
        MinecraftForge.EVENT_BUS.register(BatteryInputEnergyCapability())
        MinecraftForge.EVENT_BUS.register(CloakEnergyCapability())
        MinecraftForge.EVENT_BUS.register(ShieldJammerEnergyCapability())
        MinecraftForge.EVENT_BUS.register(GravityFieldEnergyCapability())
        MinecraftForge.EVENT_BUS.register(GravityFieldHandler())
        MinecraftForge.EVENT_BUS.register(ShieldSolidBarrier())
        MinecraftForge.EVENT_BUS.register(SolidProjectionModuleEnergyCapability())
        MinecraftForge.EVENT_BUS.register(PodShipManager)

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

        com.mechanicalskies.vsshields.blockentity.GravityFieldGeneratorBlockEntity.setEnergyInputHook { level, pos, be ->
            CreateCompat.tickGravityFieldInput(level, pos, be)
        }

        SolidProjectionModuleBlockEntity.setEnergyInputHook { level, pos, be ->
            CreateCompat.tickSolidModuleInput(level, pos, be)
        }

        // Legacy boarding pod trust callback (for old BoardingPodEntity if still present)
        BoardingPodEntity.setTrustCallback { uuid, gameTime, ticks ->
            ShieldSolidBarrier.trustPassenger(uuid, gameTime, ticks)
        }

        // VS2-ship boarding pod callbacks — wire PodShipManager to CockpitSeatEntity
        CockpitSeatEntity.setRegisterCallback { podShipId, seatEntityId, ignoredShipId, dimensionId ->
            PodShipManager.register(podShipId, seatEntityId, ignoredShipId, dimensionId)
        }
        CockpitSeatEntity.setFireCallback { seatEntityId, yaw, pitch ->
            PodShipManager.fire(seatEntityId, yaw, pitch)
        }
        CockpitSeatEntity.setRcsCallback { seatEntityId, lateralDir, boostActive ->
            PodShipManager.tryRcs(seatEntityId, lateralDir, boostActive)
        }
        CockpitSeatEntity.setTrustCallback { uuid, gameTime, ticks ->
            ShieldSolidBarrier.trustPassenger(uuid, gameTime, ticks)
        }

        // px/py/pz = mine model-space position (pre-amplified lever arm)
        GravitationalMineEntity.setPhysicsApplier { level, shipId, fx, fy, fz, px, py, pz ->
            try {
                val adapter = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
                adapter.applyWorldForceToModelPos(shipId,
                    Vector3d(fx, fy, fz), Vector3d(px, py, pz))
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
            AnalyzerScanHandler.tickCleanup(event.server)
        }
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        ShieldManager.getInstance().clear()
        CloakManager.getInstance().clear()
        GravityFieldRegistry.clear()
        SolidModuleRegistry.getInstance().clear()
        AnalyzerBlockCache.getInstance().clear()
        AnalyzerScanHandler.clear()
    }

    @SubscribeEvent
    fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        val ship = level.getShipManagingPos(event.pos) ?: return
        AnalyzerBlockCache.getInstance().onBlockPlaced(ship.id, event.pos, event.state)
    }

    @SubscribeEvent
    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        val ship = level.getShipManagingPos(event.pos) ?: return
        AnalyzerBlockCache.getInstance().onBlockRemoved(ship.id, event.pos)
    }

    /**
     * Allow players to break a parked (AIMING) boarding pod with bare hand hits.
     * After MAX_HITS punches the pod breaks and drops both multiblock items.
     */
    @SubscribeEvent
    fun onAttackPod(event: AttackEntityEvent) {
        val target = event.target as? BoardingPodEntity ?: return
        if (target.getPhase() != BoardingPodEntity.Phase.AIMING) return
        if (target.level().isClientSide) return
        event.isCanceled = true  // suppress default attack (no knockback / living-entity damage)
        if (target.onHit()) {
            val level = target.level() as ServerLevel
            fun drop(item: net.minecraft.world.item.Item) =
                level.addFreshEntity(net.minecraft.world.entity.item.ItemEntity(
                    level, target.x, target.y + 0.3, target.z,
                    net.minecraft.world.item.ItemStack(item)))
            drop(com.mechanicalskies.vsshields.registry.ModItems.BOARDING_POD_COCKPIT.get())
            drop(com.mechanicalskies.vsshields.registry.ModItems.BOARDING_POD_ENGINE.get())
            target.discard()
        }
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        ModNetwork.sendSyncToPlayer(player)
        CloakManager.getInstance().sendAllCloakedShipsToClient(player)
    }
}
