package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.anomaly.ClientAnomalyData;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModEntities;
import com.mechanicalskies.vsshields.registry.ModMenus;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.item.ItemPropertiesRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

public class VSShieldsModClient {
    private static Runnable anomalyParticleTicker;

    public static void setAnomalyParticleTicker(Runnable ticker) {
        anomalyParticleTicker = ticker;
    }

    public static void initClient() {
        // registerPackets() is now handled by EnvExecutor in ModNetwork.init()
        BlockEntityRendererRegistry.register(ModBlockEntities.SHIELD_GENERATOR.get(), ShieldRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.CLOAKING_FIELD_GENERATOR.get(),
                CloakShimmerRenderer::new);

        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_GENERATOR_MENU.get(), ShieldGeneratorScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_BATTERY_MENU.get(), ShieldBatteryScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.CLOAK_GENERATOR_MENU.get(), CloakGeneratorScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.shield_jammer_MENU.get(),
                com.mechanicalskies.vsshields.client.ShieldJammerScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.GRAVITY_FIELD_MENU.get(),
                com.mechanicalskies.vsshields.client.GravityFieldScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.SOLID_PROJECTION_MODULE_MENU.get(),
                com.mechanicalskies.vsshields.client.SolidProjectionModuleScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.RESONANCE_BEACON_MENU.get(),
                ResonanceBeaconScreen::new);
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> ShieldHudOverlay.render(graphics, delta));
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> AnalyzerHudOverlay.render(graphics, delta));
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> BoardingPodHudOverlay.render(graphics, delta));
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> VoidDepositProgressHud.render(graphics, delta));
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> AnomalyTimerHud.render(graphics, delta));

        EntityRendererRegistry.register(ModEntities.GRAVITATIONAL_MINE, ThrownItemRenderer::new);

        // Aetheric Compass angle property — drives 32 model overrides
        ItemPropertiesRegistry.register(
                com.mechanicalskies.vsshields.registry.ModItems.AETHERIC_COMPASS.get(),
                new ResourceLocation("vs_shields", "angle"),
                (stack, level, entity, seed) ->
                        com.mechanicalskies.vsshields.item.AethericCompassItem.computeAngle(level, entity, stack));

        // Ambient shield hum sound + shield break animations + helm scanner keybind
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(instance -> {
            ShieldAmbientSoundHandler.tick();
            ShieldEffectHandler.tick();
            HelmAnalyzerHandler.tick(instance);
            BoardingPodClientHandler.tick();
            ClientAnomalyData.tickPulseShake();
            if (anomalyParticleTicker != null) anomalyParticleTicker.run();
        });
    }

}
