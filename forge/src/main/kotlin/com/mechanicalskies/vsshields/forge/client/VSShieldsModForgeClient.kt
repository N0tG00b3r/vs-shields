package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.client.BoardingPodClientHandler
import com.mechanicalskies.vsshields.client.GravitationalMineModel
import com.mechanicalskies.vsshields.client.HelmAnalyzerHandler
import com.mechanicalskies.vsshields.client.VSShieldsModClient
import com.mechanicalskies.vsshields.forge.CuriosIntegration
import com.mechanicalskies.vsshields.item.TacticalGogglesItem
import com.mechanicalskies.vsshields.registry.ModEntities
import com.mechanicalskies.vsshields.registry.ModItems
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import top.theillusivec4.curios.api.client.CuriosRendererRegistry
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.slf4j.LoggerFactory

class VSShieldsModForgeClient {
    companion object {
        private val LOGGER = LoggerFactory.getLogger("vs_shields/cloak_flywheel")

        @JvmStatic
        fun clientInit(event: FMLClientSetupEvent) {
            VSShieldsModClient.initClient()
            VSShieldsModClient.setAnomalyParticleTicker { AnomalyParticleHandler.tick() }
            CloakRenderSuppressor.register()
            MinecraftForge.EVENT_BUS.register(DrillShakeHandler)
            HelmAnalyzerHandler.setGogglesChecker { player -> CuriosIntegration.hasGogglesInHeadSlot(player) }
            if (CuriosIntegration.LOADED) {
                CuriosRendererRegistry.register(ModItems.TACTICAL_GOGGLES.get()) { TacticalGogglesRenderer() }
            }
            diagnoseFlywheel()
        }

        private fun diagnoseFlywheel() {
            try {
                val cls = Class.forName("org.valkyrienskies.mod.compat.flywheel.EmbeddingShipVisual")
                LOGGER.info("EmbeddingShipVisual found: {}", cls.name)
                // Check if our mixin was applied — we inject a static 'loggedShips' field
                val mixinField = cls.declaredFields.find { it.name == "loggedShips" }
                if (mixinField != null) {
                    LOGGER.info("MixinEmbeddingShipVisual was APPLIED (loggedShips field present)")
                } else {
                    LOGGER.warn("MixinEmbeddingShipVisual NOT applied (loggedShips field missing). Fields: {}",
                        cls.declaredFields.map { it.name })
                }
                // Dump all declared methods so we can verify the target method name
                val methods = cls.declaredMethods.map { m ->
                    "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})"
                }
                LOGGER.info("EmbeddingShipVisual methods: {}", methods)
            } catch (e: ClassNotFoundException) {
                LOGGER.warn("EmbeddingShipVisual NOT found — Flywheel compat absent or VS2 lacks it: {}", e.message)
            } catch (e: Throwable) {
                LOGGER.warn("EmbeddingShipVisual check failed: {}", e.toString())
            }
        }

        @JvmStatic
        fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
            event.register(HelmAnalyzerHandler.SCAN_KEY)
            event.register(BoardingPodClientHandler.FIRE_KEY)
        }

        /** Register the layer definition so the model can be baked. */
        @JvmStatic
        fun registerLayers(event: EntityRenderersEvent.RegisterLayerDefinitions) {
            event.registerLayerDefinition(GravitationalMineModel.LAYER_LOCATION, GravitationalMineModel<*>::createBodyLayer)
            event.registerLayerDefinition(TacticalGogglesModel.LAYER_LOCATION, TacticalGogglesModel.Companion::createBodyLayer)
        }

        @JvmStatic
        fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            event.registerEntityRenderer(ModEntities.GRAVITATIONAL_MINE.get(), ::GravitationalMineRenderer)
            event.registerEntityRenderer(ModEntities.BOARDING_POD.get(), ::BoardingPodRenderer)
            event.registerEntityRenderer(ModEntities.COCKPIT_SEAT.get(), ::CockpitSeatRenderer)
        }

        /**
         * Bake the model once all layers are ready and hand it to TacticalGogglesItem
         * via a supplier so the item class stays free of client-only imports.
         */
        @JvmStatic
        fun addLayers(event: EntityRenderersEvent.AddLayers) {
            val gogglesRoot = event.entityModels.bakeLayer(TacticalGogglesModel.LAYER_LOCATION)
            val gogglesModel = TacticalGogglesModel<net.minecraft.world.entity.LivingEntity>(gogglesRoot)
            TacticalGogglesItem.setModelSupplier { gogglesModel }
        }
    }
}
