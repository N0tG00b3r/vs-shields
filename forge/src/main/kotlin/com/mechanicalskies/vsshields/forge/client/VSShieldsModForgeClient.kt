package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.client.GravitationalMineModel
import com.mechanicalskies.vsshields.client.HelmAnalyzerHandler
import com.mechanicalskies.vsshields.client.TacticalHelmModel
import com.mechanicalskies.vsshields.client.VSShieldsModClient
import com.mechanicalskies.vsshields.item.TacticalNetheriteHelm
import com.mechanicalskies.vsshields.registry.ModEntities
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

class VSShieldsModForgeClient {
    companion object {
        @JvmStatic
        fun clientInit(event: FMLClientSetupEvent) {
            VSShieldsModClient.initClient()
        }

        @JvmStatic
        fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
            event.register(HelmAnalyzerHandler.SCAN_KEY)
        }

        /** Register the layer definition so the model can be baked. */
        @JvmStatic
        fun registerLayers(event: EntityRenderersEvent.RegisterLayerDefinitions) {
            event.registerLayerDefinition(TacticalHelmModel.LAYER_LOCATION, TacticalHelmModel<*>::createLayer)
            event.registerLayerDefinition(GravitationalMineModel.LAYER_LOCATION, GravitationalMineModel<*>::createBodyLayer)
        }

        @JvmStatic
        fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            event.registerEntityRenderer(ModEntities.GRAVITATIONAL_MINE.get(), ::GravitationalMineRenderer)
        }

        /**
         * Bake the model once all layers are ready and hand it to TacticalNetheriteHelm
         * via a supplier so the item class stays free of client-only imports.
         */
        @JvmStatic
        fun addLayers(event: EntityRenderersEvent.AddLayers) {
            val root = event.entityModels.bakeLayer(TacticalHelmModel.LAYER_LOCATION)
            val model = TacticalHelmModel<net.minecraft.world.entity.LivingEntity>(root)
            TacticalNetheriteHelm.setModelSupplier { model }
        }
    }
}
