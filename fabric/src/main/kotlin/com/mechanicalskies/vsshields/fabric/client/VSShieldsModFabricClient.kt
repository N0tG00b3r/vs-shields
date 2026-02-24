package com.mechanicalskies.vsshields.fabric.client

import com.mechanicalskies.vsshields.client.VSShieldsModClient
import net.fabricmc.api.ClientModInitializer

class VSShieldsModFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        VSShieldsModClient.initClient()

        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
            com.mechanicalskies.vsshields.client.TacticalHelmModel.LAYER_LOCATION,
            com.mechanicalskies.vsshields.client.TacticalHelmModel<*>::createLayer
        )

        net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer.register(
            net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer { matrices, vertexConsumers, stack, entity, slot, light, contextModel ->
                val model = com.mechanicalskies.vsshields.client.TacticalHelmModel<net.minecraft.world.entity.LivingEntity>(
                    net.minecraft.client.Minecraft.getInstance().entityModels.bakeLayer(com.mechanicalskies.vsshields.client.TacticalHelmModel.LAYER_LOCATION)
                )
                contextModel.copyPropertiesTo(model)
                net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer.renderPart(
                    matrices, vertexConsumers, light, stack, model,
                    net.minecraft.resources.ResourceLocation("vs_shields:textures/models/armor/tactical_helm_layer_1.png")
                )
            },
            com.mechanicalskies.vsshields.registry.ModItems.TACTICAL_HELM.get()
        )
    }
}
